package io.oatg.gen;

import net.datafaker.Faker;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Produces valid values for well-known OpenAPI string formats. All values are
 * derived exclusively from the seeded {@link Random} — deterministic, no AI.
 * Unknown formats return {@link Optional#empty()} and fall back to a plain string.
 */
public final class FormatRegistry {

    private static final LocalDate DATE_BASE = LocalDate.of(2020, 1, 1);
    private static final int DATE_RANGE_DAYS = 3650;

    private final Random random;
    private Faker faker; // lazily created; shares the seeded Random

    public FormatRegistry(Random random) {
        this.random = random;
    }

    public Optional<String> generate(String format) {
        return switch (format) {
            case "uuid" -> Optional.of(new UUID(random.nextLong(), random.nextLong()).toString());
            case "email", "idn-email" -> Optional.of(faker().internet().emailAddress());
            case "date" -> Optional.of(randomDate().toString());
            case "date-time" -> Optional.of(randomDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            case "time" -> Optional.of(randomTime().format(DateTimeFormatter.ISO_LOCAL_TIME));
            case "uri", "url" -> Optional.of("https://example.com/" + alphanumeric(8));
            case "hostname", "idn-hostname" -> Optional.of("host-" + alphanumeric(6).toLowerCase(Locale.ROOT) + ".example.com");
            case "ipv4" -> Optional.of(octet() + "." + octet() + "." + octet() + "." + octet());
            case "ipv6" -> Optional.of(randomIpv6());
            case "byte" -> Optional.of(Base64.getEncoder().encodeToString(randomBytes(9)));
            case "password" -> Optional.of("Pw1!" + alphanumeric(10));
            case "binary" -> Optional.of(alphanumeric(12)); // sent as string in JSON context
            default -> Optional.empty();
        };
    }

    private Faker faker() {
        if (faker == null) {
            faker = new Faker(Locale.ENGLISH, random);
        }
        return faker;
    }

    private LocalDate randomDate() {
        return DATE_BASE.plusDays(random.nextInt(DATE_RANGE_DAYS));
    }

    private LocalTime randomTime() {
        return LocalTime.of(random.nextInt(24), random.nextInt(60), random.nextInt(60));
    }

    private OffsetDateTime randomDateTime() {
        return OffsetDateTime.of(randomDate(), randomTime(), ZoneOffset.UTC);
    }

    private int octet() {
        return 1 + random.nextInt(254);
    }

    private String randomIpv6() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(Integer.toHexString(random.nextInt(0x10000)));
        }
        return sb.toString();
    }

    private byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        random.nextBytes(bytes);
        return bytes;
    }

    String alphanumeric(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
