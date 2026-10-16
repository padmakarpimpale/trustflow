package com.padmakar.trustflow.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative routing rule, not a learned model or evidence verification. */
public final class TextReviewGuard {

    public static final String VERSION = "text-guard-v1";
    private static final Pattern REVIEW_LANGUAGE = Pattern.compile(
        "\\b(no|not|never|without|missing|lost|pending|awaiting|unapproved|" +
            "unauthori[sz]ed|unclear|uncertain|unverified|disputed|" +
            "exception|personal|private|duplicate|fake|fabricated|" +
            "bypass|conflicting|incomplete)\\b|\\b[a-z]+n't\\b"
    );

    private TextReviewGuard() {}

    public static boolean requiresReview(String title, String description) {
        String text = Normalizer.normalize(title + " " + description, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replace('\u2019', '\'');
        return REVIEW_LANGUAGE.matcher(text).find();
    }
}
