package digital.anchorstudio.soundprint

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ClapFeatureExtractorTest {
    @Test fun repeatPadMatchesClapProcessorRepeatpadForNonDivisibleAudio() {
        val result = ClapFeatureExtractor.repeatPad(floatArrayOf(1f,2f,3f,4f,5f,6f),10)
        assertArrayEquals(floatArrayOf(1f,2f,3f,4f,5f,6f,0f,0f,0f,0f),result,0f)
    }

    @Test fun repeatPadRepeatsWholeCopiesBeforeZeroPadding() {
        val result = ClapFeatureExtractor.repeatPad(floatArrayOf(0.25f,-0.25f),10)
        assertArrayEquals(floatArrayOf(0.25f,-0.25f,0.25f,-0.25f,0.25f,-0.25f,0.25f,-0.25f,0.25f,-0.25f),result,0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun repeatPadRejectsEmptyAudio() {
        ClapFeatureExtractor.repeatPad(floatArrayOf(),10)
    }
}
