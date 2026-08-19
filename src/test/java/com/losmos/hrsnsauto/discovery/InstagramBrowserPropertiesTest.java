package com.losmos.hrsnsauto.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InstagramBrowserPropertiesTest {

	@Test
	void defaultsToDisabledHeadedProjectLocalProfileAndTenItems() {
		InstagramBrowserProperties properties = new InstagramBrowserProperties();

		assertThat(properties.isAutomationEnabled()).isFalse();
		assertThat(properties.isHeadless()).isFalse();
		assertThat(properties.getUserDataDir())
				.isEqualTo(InstagramBrowserProperties.DEFAULT_USER_DATA_DIR);
		assertThat(properties.validatedBatchSize()).isEqualTo(10);
	}

	@Test
	void acceptsMaximumFifteenAndRejectsValuesOutsideRange() {
		InstagramBrowserProperties properties = new InstagramBrowserProperties();
		properties.setBatchSize(15);
		assertThat(properties.validatedBatchSize()).isEqualTo(15);

		properties.setBatchSize(0);
		assertThatThrownBy(properties::validatedBatchSize)
				.isInstanceOf(InstagramBrowserOperationException.class)
				.hasMessageContaining("1 이상 15 이하");

		properties.setBatchSize(16);
		assertThatThrownBy(properties::validatedBatchSize)
				.isInstanceOf(InstagramBrowserOperationException.class)
				.hasMessageContaining("1 이상 15 이하");
	}
}
