package com.flechazo.polaris_vehicle.util;

import com.flechazo.hkt.Either;
import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.hkt.business.util.OptionalOps;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;

import java.io.Reader;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CodecUtil {
    private CodecUtil() {
    }

    public static <A> A readJson(Codec<A> codec, Reader reader) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
    }

    public static <E extends Enum<E>> Codec<E> enumCodec(Class<E> enumClass) {
        var values = Arrays.stream(enumClass.getEnumConstants())
                .collect(Collectors.toMap(value -> value.name().toLowerCase(Locale.ROOT), Function.identity()));
        String expected = String.join(", ", values.keySet());
        return Codec.STRING.comapFlatMap(name -> Pathway.nullable(values.get(name.toLowerCase(Locale.ROOT)))
                .map(DataResult::success)
                .getOrElseGet(() -> DataResult.error(() -> "Unknown " + enumClass.getSimpleName() + " value '"
                        + name + "'; expected one of: " + expected)),
                value -> value.name().toLowerCase(Locale.ROOT));
    }

    public static <A> MapCodec<Maybe<A>> maybeFieldCodec(String fieldName, Codec<A> elementCodec) {
        return elementCodec.optionalFieldOf(fieldName).xmap(OptionalOps::toMaybe, OptionalOps::fromMaybe);
    }

    public static <A> Either<String, A> toEither(DataResult<A> result) {
        return Pathway.maybe(OptionalOps.toMaybe(result.error()))
                .<Either<String, A>>map(error -> Either.left(error.message()))
                .getOrElseGet(() -> OptionalOps.toMaybe(result.result())
                        .toEither("Codec completed without a value"));
    }
}
