package com.losmos.hrsnsauto.discovery;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class InstagramBrowserErrorSanitizer {

	private static final int MAX_LENGTH = 300;
	private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
			"(?i)(password|passwd|access[_-]?token|sessionid|csrftoken|cookie|authorization)"
					+ "([\"']?\\s*[:=]\\s*[\"']?)[^\\s;&,}\"']+");
	private static final Pattern BEARER_VALUE = Pattern.compile("(?i)(bearer\\s+)[^\\s,]+");

	public String sanitize(String value, String sensitiveDirectory) {
		if (value == null || value.isBlank()) {
			return "";
		}
		// Playwright의 후속 call log나 page text가 오류에 붙어도 첫 줄 밖은 운영자 메시지로 내보내지 않는다.
		String firstLine = value.split("[\\r\\n]", 2)[0];
		String sanitized = firstLine;
		if (sensitiveDirectory != null && !sensitiveDirectory.isBlank()) {
			sanitized = sanitized.replace(sensitiveDirectory, "[BROWSER_PROFILE]");
		}
		sanitized = BEARER_VALUE.matcher(sanitized).replaceAll("$1[REDACTED]");
		sanitized = SECRET_ASSIGNMENT.matcher(sanitized).replaceAll("$1$2[REDACTED]");
		sanitized = sanitized.replaceAll("[\\p{Cntrl}&&[^\\t]]", " ").strip();
		if (sanitized.length() <= MAX_LENGTH) {
			return sanitized;
		}
		return sanitized.substring(0, MAX_LENGTH);
	}
}
