package com.losmos.hrsnsauto.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InstagramMetricParserTest {

	private final InstagramMetricParser parser = new InstagramMetricParser();

	@Test
	void parsesPlainGroupedKoreanAndEnglishCompactCounts() {
		assertThat(parser.parse("523")).contains(523L);
		assertThat(parser.parse("1,234")).contains(1_234L);
		assertThat(parser.parse("1.234")).contains(1_234L);
		assertThat(parser.parse("4.8천")).contains(4_800L);
		assertThat(parser.parse("1.2만")).contains(12_000L);
		assertThat(parser.parse("4.8K")).contains(4_800L);
		assertThat(parser.parse("1.2M")).contains(1_200_000L);
		assertThat(parser.parse("4,8 K")).contains(4_800L);
	}

	@Test
	void roundsCompactDecimalsHalfUpAfterApplyingMultiplier() {
		assertThat(parser.parse("1.2345K")).contains(1_235L);
		assertThat(parser.parse("0.0004만")).contains(4L);
		assertThat(parser.parse("0.00005만")).contains(1L);
	}

	@Test
	void rejectsUnsupportedAmbiguousNegativeAndOverflowValues() {
		assertThat(parser.parse("1.2")).isEmpty();
		assertThat(parser.parse("-4.8K")).isEmpty();
		assertThat(parser.parse("about 2K")).isEmpty();
		assertThat(parser.parse("2B")).isEmpty();
		assertThat(parser.parse("9,223,372,036,854,775,808")).isEmpty();
		assertThat(parser.parse(null)).isEmpty();
	}
}
