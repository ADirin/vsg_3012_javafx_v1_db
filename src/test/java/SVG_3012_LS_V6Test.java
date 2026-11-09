import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SVG_3012_LS_V6Test {

    @Nested
    @DisplayName("Calculation Tests (calTime)")
    class CalculationTests {

        @Test
        @DisplayName("Should correctly calculate time for valid distance and speed")
        void testCalTimeValidInput() {
            double distance = 100.0;
            double speed = 50.0;
            double expectedTime = 2.0;

            double actualTime = SVG_3012_LS_V6.calTime(distance, speed);

            assertEquals(expectedTime, actualTime, 0.0001, "100 km at 50 km/h should take 2.0 hours");
        }

        @Test
        @DisplayName("Should return zero when speed is zero or negative")
        void testCalTimeInvalidSpeed() {
            assertEquals(0.0, SVG_3012_LS_V6.calTime(100.0, 0.0), "Zero speed should return 0");
            assertEquals(0.0, SVG_3012_LS_V6.calTime(100.0, -50.0), "Negative speed should return 0");
        }

        @Test
        @DisplayName("Should return zero when distance is negative")
        void testCalTimeNegativeDistance() {
            assertEquals(0.0, SVG_3012_LS_V6.calTime(-100.0, 50.0), "Negative distance should return 0");
        }

        @Test
        @DisplayName("Should return zero when distance is zero")
        void testCalTimeZeroDistance() {
            assertEquals(0.0, SVG_3012_LS_V6.calTime(0.0, 50.0), "Zero distance should return 0");
        }
    }

    @Nested
    @DisplayName("Digit Normalization Tests (normalizeDigits)")
    class NormalizeDigitsTests {

        @Test
        @DisplayName("Should handle null and empty strings gracefully")
        void testNullAndEmpty() {
            assertEquals("", SVG_3012_LS_V6.normalizeDigits(null));
            assertEquals("", SVG_3012_LS_V6.normalizeDigits(""));
        }

        @ParameterizedTest(name = "Normalizes ''{0}'' to ''{1}''")
        @CsvSource({
                "۱۲۳۴۵۶۷۸۹۰, 1234567890", // Persian digits -> ASCII
                "١٢٣٤٥٦٧٨٩٠, 1234567890", // Arabic-Indic digits -> ASCII
                "۱۲٫۵, 12.5",              // Persian decimal separator (٫) -> dot
                "۱۲.۵, 12.5",              // Dot remains dot
                "۱۲،۵, 12.5",              // Arabic comma (،) -> dot
                "۱٬۰۰۰, 1000"              // Thousands separator (٬) stripped
        })
        void testNormalizeDigits(String input, String expected) {
            assertEquals(expected, SVG_3012_LS_V6.normalizeDigits(input));
        }

        @Test
        @DisplayName("Should preserve standard ASCII characters while stripping spaces according to implementation")
        void testMixedString() {
            String input = "Speed: ۵۰ km/h";
            String expected = "Speed:50km/h"; // Spaces are stripped by normalizeDigits
            assertEquals(expected, SVG_3012_LS_V6.normalizeDigits(input));
        }
    }

    @Nested
    @DisplayName("Local Digits Conversion Tests (toLocalDigits)")
    class ToLocalDigitsTests {

        @Test
        @DisplayName("Should handle null and empty strings")
        void testNullAndEmpty() {
            assertEquals("", SVG_3012_LS_V6.toLocalDigits(null, true));
            assertEquals("", SVG_3012_LS_V6.toLocalDigits("", false));
        }

        @Test
        @DisplayName("Should convert ASCII numbers and decimal points to Persian digits")
        void testToPersianDigits() {
            String input = "12.5";
            String expected = "۱۲٫۵";
            assertEquals(expected, SVG_3012_LS_V6.toLocalDigits(input, true));
        }

        @Test
        @DisplayName("Should convert ASCII numbers and decimal points to Arabic-Indic digits")
        void testToArabicDigits() {
            String input = "12.5";
            String expected = "١٢٫٥";
            assertEquals(expected, SVG_3012_LS_V6.toLocalDigits(input, false));
        }

        @Test
        @DisplayName("Should retain non-numeric content while converting digits")
        void testMixedTextToLocalDigits() {
            String input = "Result: 100 km";
            String expectedPersian = "Result: ۱۰۰ km";
            assertEquals(expectedPersian, SVG_3012_LS_V6.toLocalDigits(input, true));
        }
    }
}