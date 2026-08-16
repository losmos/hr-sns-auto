package com.losmos.hrsnsauto.candidate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class EligibilityPolicy {

	private static final int FOLLOWER_EXCLUSION_THRESHOLD = 10_000;

	public EligibilityDecision assess(Candidate candidate, Collection<CandidateEvidence> evidence) {
		Objects.requireNonNull(candidate, "candidate는 필수이다");
		Collection<CandidateEvidence> safeEvidence = evidence == null ? List.of() : evidence;

		List<String> exclusionReasons = hardExclusionReasons(candidate);
		if (!exclusionReasons.isEmpty()) {
			// Review 조건이 함께 있어도 명시적 제외 조건이 우선한다.
			return new EligibilityDecision(EligibilityStatus.INELIGIBLE, String.join("; ", exclusionReasons));
		}

		List<String> reviewReasons = reviewReasons(candidate, safeEvidence);
		if (!reviewReasons.isEmpty()) {
			return new EligibilityDecision(EligibilityStatus.REVIEW_REQUIRED, String.join("; ", reviewReasons));
		}

		return new EligibilityDecision(EligibilityStatus.ELIGIBLE, "필수 검증 조건을 모두 충족함");
	}

	private List<String> hardExclusionReasons(Candidate candidate) {
		List<String> reasons = new ArrayList<>();

		if (candidate.getProfession() == Profession.KOREAN_MEDICINE) {
			reasons.add("한의사 또는 한의계열 profession으로 제외됨");
		}
		if (candidate.getProfession() == Profession.OTHER) {
			reasons.add("의사·약사 대상 profession이 아니어서 제외됨");
		}
		if (candidate.getFollowerCount() != null
				&& candidate.getFollowerCount() >= FOLLOWER_EXCLUSION_THRESHOLD) {
			reasons.add("follower 10,000 이상으로 제외됨");
		}
		if (candidate.getHairTransplantRelation() == HairTransplantRelation.RELATED) {
			reasons.add("모발이식 관련성이 확인되어 제외됨");
		}

		return reasons;
	}

	private List<String> reviewReasons(Candidate candidate, Collection<CandidateEvidence> evidence) {
		List<String> reasons = new ArrayList<>();

		if (candidate.getProfession() == null || candidate.getProfession() == Profession.UNKNOWN) {
			reasons.add("profession이 확인되지 않음");
		}
		if (candidate.getFollowerCount() == null) {
			reasons.add("follower 수가 확인되지 않음");
		}
		if (candidate.getFollowerCount() != null && candidate.getFollowerCount() < 0) {
			// Form과 DB도 음수를 막지만 policy를 직접 호출해도 eligible이 되지 않게 한다.
			reasons.add("follower 수가 올바르지 않음");
		}
		if (candidate.getHairTransplantRelation() == null
				|| candidate.getHairTransplantRelation() == HairTransplantRelation.UNKNOWN) {
			reasons.add("모발이식 관련성이 확인되지 않음");
		}
		if (candidate.getHairTransplantRelation() == HairTransplantRelation.NOT_RELATED) {
			boolean hasConflictingHairEvidence = evidence.stream()
					.filter(Objects::nonNull)
					.anyMatch(item -> item.getType() == EvidenceType.HAIR_TRANSPLANT
							&& item.getHairTransplantFinding()
									== HairTransplantEvidenceFinding.SUPPORTS_RELATED);
			if (hasConflictingHairEvidence) {
				reasons.add("모발이식 관련성을 지지하는 evidence가 있어 NOT_RELATED 판정과 상충함");
			}

			boolean hasStrongHairEvidence = evidence.stream()
					.filter(Objects::nonNull)
					.anyMatch(item -> item.getType() == EvidenceType.HAIR_TRANSPLANT
							&& item.getHairTransplantFinding()
									== HairTransplantEvidenceFinding.SUPPORTS_NOT_RELATED
							&& item.getStrength() == EvidenceStrength.STRONG
							&& hasSourceUrl(item));
			long independentWeakHairSources = evidence.stream()
					.filter(Objects::nonNull)
					.filter(item -> item.getType() == EvidenceType.HAIR_TRANSPLANT)
					.filter(item -> item.getHairTransplantFinding()
							== HairTransplantEvidenceFinding.SUPPORTS_NOT_RELATED)
					.filter(item -> item.getStrength() == EvidenceStrength.WEAK)
					.filter(this::hasSourceUrl)
					.map(item -> item.getSourceUrl().trim())
					.distinct()
					.count();

			if (!hasStrongHairEvidence && independentWeakHairSources < 2) {
				reasons.add("모발이식 비관련성을 뒷받침하는 공개 근거가 최소 기준을 충족하지 못함");
			}
		}

		boolean hasStrongProfessionEvidence = evidence.stream()
				.filter(Objects::nonNull)
				.anyMatch(item -> item.getType() == EvidenceType.PROFESSION
						&& item.getStrength() == EvidenceStrength.STRONG
						&& hasSourceUrl(item));
		long independentWeakProfessionSources = evidence.stream()
				.filter(Objects::nonNull)
				.filter(item -> item.getType() == EvidenceType.PROFESSION)
				.filter(item -> item.getStrength() == EvidenceStrength.WEAK)
				.filter(this::hasSourceUrl)
				.map(item -> item.getSourceUrl().trim())
				.distinct()
				.count();
		boolean hasIdentityEvidence = evidence.stream()
				.filter(Objects::nonNull)
				.anyMatch(item -> item.getType() == EvidenceType.IDENTITY && hasSourceUrl(item));

		if (!hasStrongProfessionEvidence && independentWeakProfessionSources < 2) {
			reasons.add("profession 공개 근거가 최소 기준을 충족하지 못함");
		}
		if (!hasIdentityEvidence) {
			reasons.add("identity evidence가 없음");
		}

		return reasons;
	}

	private boolean hasSourceUrl(CandidateEvidence evidence) {
		return evidence.getSourceUrl() != null && !evidence.getSourceUrl().isBlank();
	}
}
