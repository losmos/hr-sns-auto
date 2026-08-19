package com.losmos.hrsnsauto.discovery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class InstagramMetricParser {

	private static final Pattern INTEGER = Pattern.compile("^[0-9]+$");
	private static final Pattern GROUPED_INTEGER = Pattern.compile("^[0-9]{1,3}([,.][0-9]{3})+$");
	private static final Pattern COMPACT_NUMBER = Pattern.compile("^[0-9]+([.,][0-9]+)?$");

	/**
	 * 화면의 compact count를 정수로 바꾼다. 소수 compact count는 multiplier를 적용한 뒤
	 * 0.5 이상을 올리는 HALF_UP 규칙을 사용한다. 지원하지 않거나 모호한 표현은 추정하지 않는다.
	 */
	public Optional<Long> parse(String visibleCount) {
		if (visibleCount == null || visibleCount.isBlank()) {
			return Optional.empty();
		}
		String normalized = Normalizer.normalize(visibleCount, Normalizer.Form.NFKC)
				.replaceAll("[\\s\\p{Zs}]", "")
				.toLowerCase(Locale.ROOT);

		BigDecimal multiplier = BigDecimal.ONE;
		if (normalized.endsWith("천") || normalized.endsWith("k")) {
			multiplier = BigDecimal.valueOf(1_000);
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		else if (normalized.endsWith("만")) {
			multiplier = BigDecimal.valueOf(10_000);
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		else if (normalized.endsWith("m")) {
			multiplier = BigDecimal.valueOf(1_000_000);
			normalized = normalized.substring(0, normalized.length() - 1);
		}

		try {
			BigDecimal numeric;
			if (multiplier.compareTo(BigDecimal.ONE) > 0) {
				if (!COMPACT_NUMBER.matcher(normalized).matches()) {
					return Optional.empty();
				}
				numeric = new BigDecimal(normalized.replace(',', '.'));
			}
			else if (INTEGER.matcher(normalized).matches()) {
				numeric = new BigDecimal(normalized);
			}
			else if (GROUPED_INTEGER.matcher(normalized).matches()) {
				numeric = new BigDecimal(normalized.replace(",", "").replace(".", ""));
			}
			else {
				return Optional.empty();
			}

			long value = numeric.multiply(multiplier)
					.setScale(0, RoundingMode.HALF_UP)
					.longValueExact();
			return value < 0 ? Optional.empty() : Optional.of(value);
		}
		catch (ArithmeticException exception) {
			return Optional.empty();
		}
	}
}
