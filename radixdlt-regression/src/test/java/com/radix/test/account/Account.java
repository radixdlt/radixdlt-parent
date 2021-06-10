package com.radix.test.account;

import com.radix.test.Utils;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.RadixApi;
import com.radixdlt.client.lib.dto.Balance;
import com.radixdlt.client.lib.dto.TokenInfo;
import com.radixdlt.client.lib.dto.TokenBalances;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.stream.Collectors;

/**
 * A wrapper around an api client + a keypair
 */
public final class Account implements RadixApi {
    private static final Logger logger = LogManager.getLogger();

    private final RadixApi client;
    private final ECKeyPair keyPair;
    private final AccountAddress address;
    private final TokenInfo nativeToken;

    private Account(RadixApi client, ECKeyPair keyPair, TokenInfo nativeToken) {
        this.client = client;
        this.keyPair = keyPair;
        this.address = AccountAddress.create(keyPair.getPublicKey());
        this.nativeToken = nativeToken;
    }

    @Override
    public RadixApi withTrace() {
        client.withTrace();
        return this;
    }

    @Override
    public Network network() {
        return client.network();
    }

    @Override
    public Transaction transaction() {
        return client.transaction();
    }

    @Override
    public Token token() {
        return client.token();
    }

    @Override
    public Local local() {
        return client.local();
    }

    @Override
    public SingleAccount account() {
        return client.account();
    }

    @Override
    public Validator validator() {
        return client.validator();
    }

    @Override
    public Api api() {
        return client.api();
    }

    @Override
    public Consensus consensus() {
        return client.consensus();
    }

    @Override
    public Mempool mempool() {
        return client.mempool();
    }

    @Override
    public RadixEngine radixEngine() {
        return client.radixEngine();
    }

    @Override
    public Sync sync() {
        return client.sync();
    }

    @Override
    public Ledger ledger() {
        return client.ledger();
    }

    public AccountAddress getAddress() {
        return address;
    }

    public ECKeyPair getKeyPair() {
        return keyPair;
    }

    public static Result<Account> initialize(String jsonRpcUrl) {
        return RadixApi.connect(jsonRpcUrl)
                .flatMap(api -> api.token().describeNative().map(nativeToken -> {
                    var newAccount = new Account(api, ECKeyPair.generateNew(), nativeToken);
                    logger.trace("Generated new account with address: {}", newAccount.getAddress());
                    logger.trace("New account connected to {}", jsonRpcUrl);
                    logger.trace("Network's native token is {}({})", nativeToken.getName(), nativeToken.getSymbol());
                    return newAccount;
                }));
    }

    /**
     * returns the (already queried) native token
     */
    public TokenInfo getNativeToken() {
        return nativeToken;
    }

    public Result<Balance> ownNativeTokenBalance() {
        Balance zeroNativeTokenBalance = Balance.create(nativeToken.getRri(), UInt256.ZERO);
        return ownTokenBalances().map(tokenBalancesDTO -> {
            if (tokenBalancesDTO.getTokenBalances().size() == 0) {
                return zeroNativeTokenBalance;
            }
            var balances = tokenBalancesDTO.getTokenBalances().stream().filter(balance ->
                    balance.getRri().equals(nativeToken.getRri())).collect(Collectors.toList());
            return balances.isEmpty() ? zeroNativeTokenBalance : balances.get(0);
        });
    }

    public Balance getOwnNativeTokenBalance() {
        return ownNativeTokenBalance().fold(Utils::toTestFailureException, balance -> balance);
    }

    public Result<TokenBalances> ownTokenBalances() {
        return client.account().balances(address);
    }
}
