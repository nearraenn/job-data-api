package com.ata.jobdata.domain;

/**
 * One survey row after normalisation. Every field is nullable — the source is a public survey where
 * most columns were optional.
 *
 * <p>{@code salary} is the comparable annual number and {@code salaryRaw} is exactly what the
 * respondent typed, so normalising never destroys the original answer.
 */
public record JobRecord(
        int id,
        String timestamp,
        String employer,
        String location,
        String jobTitle,
        Integer yearsAtEmployer,
        Integer yearsOfExperience,
        Long salary,
        String salaryCurrency,
        String salaryRaw,
        String signingBonus,
        String annualBonus,
        String annualStockValue,
        String gender,
        String additionalComments) {
}
