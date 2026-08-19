package com.ata.jobdata.model;

/**
 * One survey row after normalisation. Every field is nullable — the source is a public survey where
 * most columns were optional.
 *
 * <p>{@code salary}/{@code yearsAtEmployer}/{@code yearsOfExperience} are the comparable normalised
 * numbers; their {@code *Raw} counterparts are exactly what the respondent typed, so normalising
 * never destroys the original answer — including for rows where normalising gave up and returned
 * null (e.g. a negative "no answer" sentinel).
 */
public record JobRecord(
        int id,
        String timestamp,
        String employer,
        String location,
        String jobTitle,
        Double yearsAtEmployer,
        String yearsAtEmployerRaw,
        Double yearsOfExperience,
        String yearsOfExperienceRaw,
        Long salary,
        String salaryCurrency,
        String salaryRaw,
        String signingBonus,
        String annualBonus,
        String annualStockValue,
        String gender,
        String additionalComments) {
}
