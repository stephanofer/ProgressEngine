package com.stephanofer.progressengine.localization;

import com.stephanofer.progressengine.config.NumberFormatSettings;
import java.math.BigInteger;

public final class PointsNumberFormatter {
    private PointsNumberFormatter() {
    }

    public static String raw(long value) {
        return Long.toString(value);
    }

    public static String formatted(long value, NumberFormatSettings settings) {
        BigInteger number = BigInteger.valueOf(value);
        boolean negative = number.signum() < 0;
        if (negative) {
            number = number.negate();
        }
        String digits = number.toString();
        StringBuilder result = new StringBuilder(digits.length() + digits.length() / 3 + 1);
        if (negative) {
            result.append('-');
        }
        int firstGroup = digits.length() % 3;
        if (firstGroup == 0) {
            firstGroup = 3;
        }
        result.append(digits, 0, firstGroup);
        for (int index = firstGroup; index < digits.length(); index += 3) {
            result.append(settings.groupingSeparator()).append(digits, index, index + 3);
        }
        return result.toString();
    }

    public static String compact(long value, NumberFormatSettings settings) {
        BigInteger number = BigInteger.valueOf(value);
        boolean negative = number.signum() < 0;
        if (negative) {
            number = number.negate();
        }
        NumberFormatSettings.CompactMagnitude chosen = null;
        for (NumberFormatSettings.CompactMagnitude magnitude : NumberFormatSettings.CompactMagnitude.values()) {
            if (number.compareTo(BigInteger.valueOf(magnitude.divisor())) >= 0) {
                chosen = magnitude;
            }
        }
        if (chosen == null) {
            return raw(value);
        }

        BigInteger divisor = BigInteger.valueOf(chosen.divisor());
        BigInteger scale = BigInteger.TEN.pow(settings.compactDecimals());
        BigInteger scaled = number.multiply(scale).divide(divisor);
        BigInteger whole = scaled.divide(scale);
        BigInteger fraction = scaled.remainder(scale);

        StringBuilder result = new StringBuilder();
        if (negative) {
            result.append('-');
        }
        result.append(whole);
        if (settings.compactDecimals() > 0 && fraction.signum() > 0) {
            String fractionDigits = fraction.toString();
            while (fractionDigits.length() < settings.compactDecimals()) {
                fractionDigits = "0" + fractionDigits;
            }
            int trim = fractionDigits.length();
            while (trim > 0 && fractionDigits.charAt(trim - 1) == '0') {
                trim--;
            }
            if (trim > 0) {
                result.append(settings.decimalSeparator()).append(fractionDigits, 0, trim);
            }
        }
        if (settings.compactSpace()) {
            result.append(' ');
        }
        result.append(settings.compactSuffixes().get(chosen));
        return result.toString();
    }
}
