/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.crypto;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.BasicAgreement;
import org.bouncycastle.crypto.DerivationFunction;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.KeyParser;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.DerivationParameters;
import org.bouncycastle.crypto.generators.EphemeralKeyPairGenerator;
import org.bouncycastle.crypto.params.IESParameters;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.IESWithCipherParameters;
import org.bouncycastle.crypto.params.KDFParameters;
import org.bouncycastle.crypto.params.MGFParameters;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.Pack;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Support class for constructing integrated encryption cipher
 * for doing basic message exchanges on top of key agreement ciphers.
 * Follows the description given in IEEE Std 1363a with a couple of changes:
 * - Hash the MAC key before use
 * - Include the encryption IV in the MAC computation
 */
public class IESEngine {
    private final Digest hash;
    private BasicAgreement agree;
    private DerivationFunction kdf;
    private Mac mac;
    private BufferedBlockCipher cipher;

    private boolean forEncryption;
    private CipherParameters privParam, pubParam;
    private IESParameters param;

    private byte[] v;
    private EphemeralKeyPairGenerator keyPairGenerator;
    private KeyParser keyParser;
    private byte[] iv;
    boolean hashK2 = true;

    /**
     * set up for use with stream mode, where the key derivation function
     * is used to provide a stream of bytes to xor with the message.
     *  @param agree the key agreement used as the basis for the encryption
     * @param kdf    the key derivation function used for byte generation
     * @param mac    the message authentication code generator for the message
     * @param hash   hash ing function
     * @param cipher the actual cipher
     */
    public IESEngine(
        BasicAgreement agree,
        DerivationFunction kdf,
        Mac mac,
        Digest hash,
        BufferedBlockCipher cipher
    ) {
        this.agree = agree;
        this.kdf = kdf;
        this.mac = mac;
        this.hash = hash;
        this.cipher = cipher;
    }

    /**
     * Initialise the encryptor.
     *
     * @param forEncryption whether or not this is encryption/decryption.
     * @param privParam     our private key parameters
     * @param pubParam      the recipient's/sender's public key parameters
     * @param params        encoding and derivation parameters, may be wrapped to include an IV for an underlying block cipher.
     */
    public void init(
        boolean forEncryption,
        CipherParameters privParam,
        CipherParameters pubParam,
        CipherParameters params
    ) {
        this.forEncryption = forEncryption;
        this.privParam = privParam;
        this.pubParam = pubParam;
        this.v = new byte[0];

        extractParams(params);
    }

    /**
     * Initialise the encryptor.
     *
     * @param publicKey      the recipient's/sender's public key parameters
     * @param params         encoding and derivation parameters, may be wrapped to include an IV for an underlying block cipher.
     * @param ephemeralKeyPairGenerator             the ephemeral key pair generator to use.
     */
    public void init(
        AsymmetricKeyParameter publicKey,
        CipherParameters params,
        EphemeralKeyPairGenerator ephemeralKeyPairGenerator
    ) {
        this.forEncryption = true;
        this.pubParam = publicKey;
        this.keyPairGenerator = ephemeralKeyPairGenerator;

        extractParams(params);
    }

    /**
     * Initialise the encryptor.
     *
     * @param privateKey      the recipient's private key.
     * @param params          encoding and derivation parameters, may be wrapped to include an IV for an underlying block cipher.
     * @param publicKeyParser the parser for reading the ephemeral public key.
     */
    public void init(AsymmetricKeyParameter privateKey, CipherParameters params, KeyParser publicKeyParser) {
        this.forEncryption = false;
        this.privParam = privateKey;
        this.keyParser = publicKeyParser;

        extractParams(params);
    }

    private void extractParams(CipherParameters params) {
        if (params instanceof ParametersWithIV) {
            this.iv = ((ParametersWithIV) params).getIV();
            this.param = (IESParameters) ((ParametersWithIV) params).getParameters();
        } else {
            this.iv = null;
            this.param = (IESParameters) params;
        }
    }

    private byte[] encryptBlock(
        byte[] in,
        int inOff,
        int inLen,
        byte[] macData) throws InvalidCipherTextException {
        byte[] c, k, k1, k2;
        int len;

        if (cipher == null) {
            // Streaming mode.
            k1 = new byte[inLen];
            k2 = new byte[param.getMacKeySize() / 8];
            k = new byte[k1.length + k2.length];

            kdf.generateBytes(k, 0, k.length);
            System.arraycopy(k, 0, k1, 0, k1.length);
            System.arraycopy(k, inLen, k2, 0, k2.length);

            c = new byte[inLen];

            for (int i = 0; i != inLen; i++) {
                c[i] = (byte) (in[inOff + i] ^ k1[i]);
            }
            len = inLen;
        } else {
            // Block cipher mode.
            k1 = new byte[((IESWithCipherParameters) param).getCipherKeySize() / 8];
            k2 = new byte[param.getMacKeySize() / 8];
            k = new byte[k1.length + k2.length];

            kdf.generateBytes(k, 0, k.length);
            System.arraycopy(k, 0, k1, 0, k1.length);
            System.arraycopy(k, k1.length, k2, 0, k2.length);

            // If iv provided use it to initialise the cipher
            if (iv != null) {
                cipher.init(true, new ParametersWithIV(new KeyParameter(k1), iv));
            } else {
                cipher.init(true, new KeyParameter(k1));
            }

            c = new byte[cipher.getOutputSize(inLen)];
            len = cipher.processBytes(in, inOff, inLen, c, 0);
            len += cipher.doFinal(c, len);
        }

        // Convert the length of the encoding vector into a byte array.
        byte[] p2 = param.getEncodingV();

        // Apply the MAC.
        byte[] t = new byte[mac.getMacSize()];

        byte[] k2a;
        if (hashK2) {
            k2a = new byte[hash.getDigestSize()];
            hash.reset();
            hash.update(k2, 0, k2.length);
            hash.doFinal(k2a, 0);
        } else {
            k2a = k2;
        }
        mac.init(new KeyParameter(k2a));
        mac.update(iv, 0, iv.length);
        mac.update(c, 0, c.length);
        if (p2 != null) {
            mac.update(p2, 0, p2.length);
        }
        if (v.length != 0 && p2 != null) {
            byte[] l2 = new byte[4];
            Pack.intToBigEndian(p2.length * 8, l2, 0);
            mac.update(l2, 0, l2.length);
        }

        if (macData != null) {
            mac.update(macData, 0, macData.length);
        }

        mac.doFinal(t, 0);

        // Output the triple (V,C,T).
        final var output = new byte[v.length + len + t.length];
        System.arraycopy(v, 0, output, 0, v.length);
        System.arraycopy(c, 0, output, v.length, len);
        System.arraycopy(t, 0, output, v.length + len, t.length);
        return output;
    }

    private byte[] decryptBlock(
        byte[] inEnc,
        int inOff,
        int inLen,
        byte[] macData) throws InvalidCipherTextException {
        byte[] m, k, k1, k2;
        int len;

        // Ensure that the length of the input is greater than the MAC in bytes
        if (inLen <= (param.getMacKeySize() / 8)) {
            throw new InvalidCipherTextException("Length of input must be greater than the MAC");
        }

        if (cipher == null) {
            // Streaming mode.
            k1 = new byte[inLen - v.length - mac.getMacSize()];
            k2 = new byte[param.getMacKeySize() / 8];
            k = new byte[k1.length + k2.length];

            kdf.generateBytes(k, 0, k.length);
            System.arraycopy(k, 0, k1, 0, k1.length);
            System.arraycopy(k, k1.length, k2, 0, k2.length);

            m = new byte[k1.length];

            for (int i = 0; i != k1.length; i++) {
                m[i] = (byte) (inEnc[inOff + v.length + i] ^ k1[i]);
            }

            len = k1.length;
        } else {
            // Block cipher mode.
            k1 = new byte[((IESWithCipherParameters) param).getCipherKeySize() / 8];
            k2 = new byte[param.getMacKeySize() / 8];
            k = new byte[k1.length + k2.length];

            kdf.generateBytes(k, 0, k.length);
            System.arraycopy(k, 0, k1, 0, k1.length);
            System.arraycopy(k, k1.length, k2, 0, k2.length);

            // If IV provide use it to initialize the cipher
            if (iv != null) {
                cipher.init(false, new ParametersWithIV(new KeyParameter(k1), iv));
            } else {
                cipher.init(false, new KeyParameter(k1));
            }

            m = new byte[cipher.getOutputSize(inLen - v.length - mac.getMacSize())];
            len = cipher.processBytes(inEnc, inOff + v.length, inLen - v.length - mac.getMacSize(), m, 0);
            len += cipher.doFinal(m, len);
        }

        // Convert the length of the encoding vector into a byte array.
        byte[] p2 = param.getEncodingV();

        // Verify the MAC.
        int end = inOff + inLen;
        byte[] t1 = Arrays.copyOfRange(inEnc, end - mac.getMacSize(), end);

        byte[] t2 = new byte[t1.length];
        byte[] k2a;
        if (hashK2) {
            k2a = new byte[hash.getDigestSize()];
            hash.reset();
            hash.update(k2, 0, k2.length);
            hash.doFinal(k2a, 0);
        } else {
            k2a = k2;
        }
        mac.init(new KeyParameter(k2a));
        mac.update(iv, 0, iv.length);
        mac.update(inEnc, inOff + v.length, inLen - v.length - t2.length);

        if (p2 != null) {
            mac.update(p2, 0, p2.length);
        }

        if (v.length != 0 && p2 != null) {
            byte[] l2 = new byte[4];
            Pack.intToBigEndian(p2.length * 8, l2, 0);
            mac.update(l2, 0, l2.length);
        }

        if (macData != null) {
            mac.update(macData, 0, macData.length);
        }

        mac.doFinal(t2, 0);

        if (!Arrays.constantTimeAreEqual(t1, t2)) {
            throw new InvalidCipherTextException("Invalid MAC.");
        }

        // Output the message.
        return Arrays.copyOfRange(m, 0, len);
    }

    public byte[] processBlock(
        byte[] in,
        int inOff,
        int inLen,
        byte[] macData
    ) throws InvalidCipherTextException {
        if (forEncryption) {
            if (keyPairGenerator != null) {
                final var ephKeyPair = keyPairGenerator.generate();
                this.privParam = ephKeyPair.getKeyPair().getPrivate();
                this.v = ephKeyPair.getEncodedPublicKey();
            }
        } else {
            if (keyParser != null) {
                final var bIn = new ByteArrayInputStream(in, inOff, inLen);
                try {
                    this.pubParam = keyParser.readKey(bIn);
                } catch (IOException e) {
                    throw new InvalidCipherTextException("unable to recover ephemeral public key: " + e.getMessage(), e);
                }

                final var encLength = (inLen - bIn.available());
                this.v = Arrays.copyOfRange(in, inOff, inOff + encLength);
            }
        }

        // Compute the common value and convert to byte array.
        agree.init(privParam);
        byte[] z = BigIntegers.asUnsignedByteArray(agree.getFieldSize(), agree.calculateAgreement(pubParam));

        // Create input to KDF.
        byte[] vz;
        vz = z;

        // Initialise the KDF.
        DerivationParameters kdfParam;
        if (kdf instanceof MGF1BytesGeneratorExt) {
            kdfParam = new MGFParameters(vz);
        } else {
            kdfParam = new KDFParameters(vz, param.getDerivationV());
        }
        kdf.init(kdfParam);

        return forEncryption
            ? encryptBlock(in, inOff, inLen, macData)
            : decryptBlock(in, inOff, inLen, macData);
    }
}
