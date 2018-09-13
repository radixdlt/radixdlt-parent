package com.radixdlt.client.core.serialization;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixUniverseType;
import com.radixdlt.client.core.atoms.AbstractConsumable;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomFeeConsumable;
import com.radixdlt.client.core.atoms.ChronoParticle;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.Consumer;
import com.radixdlt.client.core.atoms.DataParticle;
import com.radixdlt.client.core.atoms.Emission;
import com.radixdlt.client.core.atoms.MetadataMap;
import com.radixdlt.client.core.atoms.Particle;
import com.radixdlt.client.core.atoms.Payload;
import com.radixdlt.client.core.atoms.UniqueParticle;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.network.NodeRunnerData;
import com.radixdlt.client.core.util.Base64Encoded;
import com.radixdlt.client.core.util.Int128;

import static com.radixdlt.client.core.serialization.SerializationConstants.BYT_PREFIX;
import static com.radixdlt.client.core.serialization.SerializationConstants.STR_PREFIX;
import static com.radixdlt.client.core.serialization.SerializationConstants.UID_PREFIX;

public class RadixJson {

	private static String checkPrefix(String value, String prefix) {
		if (!value.startsWith(prefix)) {
			throw new IllegalStateException("JSON value does not start with prefix " + prefix);
		}
		return value.substring(prefix.length());
	}

	private static String unString(String value) {
		return value.startsWith(STR_PREFIX) ? value.substring(STR_PREFIX.length()) : value;
	}

	private static final JsonSerializer<Base64Encoded> BASE64_SERIALIZER =
		(src, typeOfSrc, context) -> new JsonPrimitive(BYT_PREFIX + src.base64());

	private static final JsonDeserializer<Payload> PAYLOAD_DESERIALIZER =
		(json, typeOfT, context) -> Payload.fromBase64(checkPrefix(json.getAsString(), BYT_PREFIX));

	private static final JsonDeserializer<ECPublicKey> PK_DESERIALIZER = (json, typeOf, context) -> {
		byte[] publicKey = Base64.decode(checkPrefix(json.getAsString(), BYT_PREFIX));
		return new ECPublicKey(publicKey);
	};

	private static final JsonDeserializer<EncryptedPrivateKey> PROTECTOR_DESERIALIZER = (json, typeOf, context) -> {
		byte[] encryptedPrivateKey = Base64.decode(checkPrefix(json.getAsString(), BYT_PREFIX));
		return new EncryptedPrivateKey(encryptedPrivateKey);
	};

	private static final JsonDeserializer<RadixUniverseType> UNIVERSE_TYPE_DESERIALIZER =
		(json, typeOf, context) -> RadixUniverseType.valueOf(json.getAsInt());

	private static final JsonDeserializer<NodeRunnerData> NODE_RUNNER_DATA_JSON_DESERIALIZER = (json, typeOf, context) -> {
		JsonObject obj = json.getAsJsonObject();
		return new NodeRunnerData(
			obj.has("host") ? unString(obj.get("host").getAsJsonObject().get("ip").getAsString()) : null,
			obj.get("system").getAsJsonObject().get("shards").getAsJsonObject().get("low").getAsLong(),
			obj.get("system").getAsJsonObject().get("shards").getAsJsonObject().get("high").getAsLong()
		);
	};

	private static final JsonSerializer<Particle> ABSTRACT_CONSUMABLE_SERIALIZER = (particle, typeOfT, context) -> {
		if (particle.getClass() == AtomFeeConsumable.class) {
			JsonObject jsonParticle = context.serialize(particle).getAsJsonObject();
			jsonParticle.addProperty("serializer", -1463653224);
			jsonParticle.addProperty("version", 100);
			return jsonParticle;
		} else if (particle.getClass() == Consumable.class) {
			JsonObject jsonParticle = context.serialize(particle).getAsJsonObject();
			jsonParticle.addProperty("serializer", 318720611);
			jsonParticle.addProperty("version", 100);
			return jsonParticle;
		} else if (particle.getClass() == Emission.class) {
			JsonObject jsonParticle = context.serialize(particle).getAsJsonObject();
			jsonParticle.addProperty("serializer", 1782261127);
			jsonParticle.addProperty("version", 100);
			return jsonParticle;
		}

		throw new RuntimeException("Unknown Particle: " + particle.getClass());
	};

	private static final JsonDeserializer<Particle> ABSTRACT_CONSUMABLE_DESERIALIZER = (json, typeOf, context) -> {
		long serializer = json.getAsJsonObject().get("serializer").getAsLong();
		if (serializer == -1463653224) {
			return context.deserialize(json.getAsJsonObject(), AtomFeeConsumable.class);
		} else if (serializer == 318720611) {
			return context.deserialize(json.getAsJsonObject(), Consumable.class);
		} else if (serializer == 1782261127) {
			return context.deserialize(json.getAsJsonObject(), Emission.class);
		} else {
			throw new RuntimeException("Unknown particle serializer: " + serializer);
		}
	};

	private static class EUIDSerializer implements JsonDeserializer<EUID>, JsonSerializer<EUID> {
		@Override
		public JsonElement serialize(EUID src, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive(UID_PREFIX + src.toString());
		}

		@Override
		public EUID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return new EUID(Int128.from(Hex.decode(checkPrefix(json.getAsString(), UID_PREFIX))));
		}
	}


	private static class ByteArraySerializer implements JsonDeserializer<byte[]>, JsonSerializer<byte[]> {
		@Override
		public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive(BYT_PREFIX + Base64.toBase64String(src));
		}

		@Override
		public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
			return Base64.decode(checkPrefix(json.getAsString(), BYT_PREFIX));
		}
	}

	private static class StringCodec implements JsonDeserializer<String>, JsonSerializer<String> {
		@Override
		public JsonElement serialize(String src, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive(STR_PREFIX + src);
		}

		@Override
		public String deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
			return unString(json.getAsString());
		}
	}

	private static class MetadataCodec implements JsonDeserializer<MetadataMap>, JsonSerializer<MetadataMap> {
		@Override
		public JsonElement serialize(MetadataMap src, Type typeOfSrc, JsonSerializationContext context) {
			JsonObject obj = new JsonObject();
			for (Map.Entry<String, String> e : src.entrySet()) {
				obj.addProperty(e.getKey(), STR_PREFIX + e.getValue());
			}
			return obj;
		}

		@Override
		public MetadataMap deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
			JsonObject obj = (JsonObject) json;
			MetadataMap map = new MetadataMap();
			for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
				map.put(e.getKey(), unString(e.getValue().getAsString()));
			}
			return map;
		}
	}

	private static final Map<Class<?>, Integer> SERIALIZERS = new HashMap<>();
	static {
		SERIALIZERS.put(Atom.class, 2019665);
		SERIALIZERS.put(ECKeyPair.class, 547221307);
		SERIALIZERS.put(ECSignature.class, -434788200);
		SERIALIZERS.put(DataParticle.class, 473758768);
		SERIALIZERS.put(UniqueParticle.class, "UNIQUEPARTICLE".hashCode());
		SERIALIZERS.put(ChronoParticle.class, "CHRONOPARTICLE".hashCode());
		SERIALIZERS.put(Consumer.class, 214856694);
	}

	private static final TypeAdapterFactory ECKEYPAIR_ADAPTER_FACTORY = new TypeAdapterFactory() {
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			final Integer serializer = SERIALIZERS.get(type.getRawType());
			if (serializer == null) {
				return null;
			}
			final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
			final TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);

			return new TypeAdapter<T>() {
				@Override
				public void write(JsonWriter out, T value) throws IOException {
					JsonElement tree = delegate.toJsonTree(value);
					if (!tree.isJsonNull()) {
						tree.getAsJsonObject().addProperty("serializer", serializer);
						tree.getAsJsonObject().addProperty("version", 100);
					}
					elementAdapter.write(out, tree);
				}

				@Override
				public T read(JsonReader in) throws IOException {
					JsonElement tree = elementAdapter.read(in);
					return delegate.fromJsonTree(tree);
				}
			};
		}
	};

	private static final Gson GSON;

	static {
		GsonBuilder gsonBuilder = new GsonBuilder()
			.registerTypeHierarchyAdapter(Base64Encoded.class, BASE64_SERIALIZER)
			.registerTypeAdapterFactory(ECKEYPAIR_ADAPTER_FACTORY)
			.registerTypeAdapter(byte[].class, new ByteArraySerializer())
			.registerTypeAdapter(AbstractConsumable.class, ABSTRACT_CONSUMABLE_SERIALIZER)
			.registerTypeAdapter(AbstractConsumable.class, ABSTRACT_CONSUMABLE_DESERIALIZER)
			.registerTypeAdapter(String.class, new StringCodec())
			.registerTypeAdapter(MetadataMap.class, new MetadataCodec())
			.registerTypeAdapter(EUID.class, new EUIDSerializer())
			.registerTypeAdapter(Payload.class, PAYLOAD_DESERIALIZER)
			.registerTypeAdapter(EncryptedPrivateKey.class, PROTECTOR_DESERIALIZER)
			.registerTypeAdapter(ECPublicKey.class, PK_DESERIALIZER)
			.registerTypeAdapter(RadixUniverseType.class, UNIVERSE_TYPE_DESERIALIZER)
			.registerTypeAdapter(NodeRunnerData.class, NODE_RUNNER_DATA_JSON_DESERIALIZER);

		GSON = gsonBuilder.create();
	}

	private RadixJson() {
	}

	public static Gson getGson() {
		return GSON;
	}
}
