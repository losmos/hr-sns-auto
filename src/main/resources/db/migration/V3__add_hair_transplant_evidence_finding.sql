ALTER TABLE candidate_evidence
    ADD COLUMN hair_transplant_finding VARCHAR(32);

-- 기존 evidence의 방향을 추측하지 않고 재검토가 필요한 상태로 안전하게 이관한다.
UPDATE candidate_evidence
SET hair_transplant_finding = 'INCONCLUSIVE'
WHERE type = 'HAIR_TRANSPLANT';

ALTER TABLE candidate_evidence
    ADD CONSTRAINT chk_candidate_evidence_hair_transplant_finding
        CHECK (
            (
                type = 'HAIR_TRANSPLANT'
                AND hair_transplant_finding IS NOT NULL
                AND hair_transplant_finding IN (
                    'SUPPORTS_NOT_RELATED',
                    'SUPPORTS_RELATED',
                    'INCONCLUSIVE'
                )
            )
            OR (
                type <> 'HAIR_TRANSPLANT'
                AND hair_transplant_finding IS NULL
            )
        );
