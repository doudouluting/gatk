/*
* Copyright (c) 2012 The Broad Institute
* 
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
* 
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package org.broadinstitute.sting.utils;

import com.google.java.contract.Ensures;
import com.google.java.contract.Requires;
import org.broadinstitute.sting.gatk.GenomeAnalysisEngine;
import org.broadinstitute.sting.utils.exceptions.ReviewedStingException;
import org.broadinstitute.sting.utils.exceptions.UserException;

import java.math.BigDecimal;
import java.util.*;

/**
 * MathUtils is a static class (no instantiation allowed!) with some useful math methods.
 *
 * @author Kiran Garimella
 */
public class MathUtils {

    /**
     * Private constructor.  No instantiating this class!
     */
    private MathUtils() {
    }

    public static final double[] log10Cache;
    public static final double[] log10FactorialCache;
    private static final double[] jacobianLogTable;
    private static final double JACOBIAN_LOG_TABLE_STEP = 0.0001;
    private static final double JACOBIAN_LOG_TABLE_INV_STEP = 1.0 / JACOBIAN_LOG_TABLE_STEP;
    private static final double MAX_JACOBIAN_TOLERANCE = 8.0;
    private static final int JACOBIAN_LOG_TABLE_SIZE = (int) (MAX_JACOBIAN_TOLERANCE / JACOBIAN_LOG_TABLE_STEP) + 1;
    private static final int MAXN = 70000;
    private static final int LOG10_CACHE_SIZE = 4 * MAXN;  // we need to be able to go up to 2*(2N) when calculating some of the coefficients

    /**
     * The smallest log10 value we'll emit from normalizeFromLog10 and other functions
     * where the real-space value is 0.0.
     */
    public final static double LOG10_P_OF_ZERO = -1000000.0;
    public final static double FAIR_BINOMIAL_PROB_LOG10_0_5 = Math.log10(0.5);

    static {
        log10Cache = new double[LOG10_CACHE_SIZE];
        log10FactorialCache = new double[LOG10_CACHE_SIZE];
        jacobianLogTable = new double[JACOBIAN_LOG_TABLE_SIZE];

        log10Cache[0] = Double.NEGATIVE_INFINITY;
        log10FactorialCache[0] = 0.0;
        for (int k = 1; k < LOG10_CACHE_SIZE; k++) {
            log10Cache[k] = Math.log10(k);
            log10FactorialCache[k] = log10FactorialCache[k-1] + log10Cache[k];
        }

        for (int k = 0; k < JACOBIAN_LOG_TABLE_SIZE; k++) {
            jacobianLogTable[k] = Math.log10(1.0 + Math.pow(10.0, -((double) k) * JACOBIAN_LOG_TABLE_STEP));

        }
    }

    /**
     * Get a random int between min and max (inclusive) using the global GATK random number generator
     *
     * @param min lower bound of the range
     * @param max upper bound of the range
     * @return a random int >= min and <= max
     */
    public static int randomIntegerInRange( final int min, final int max ) {
        return GenomeAnalysisEngine.getRandomGenerator().nextInt(max - min + 1) + min;
    }

    // A fast implementation of the Math.round() method.  This method does not perform
    // under/overflow checking, so this shouldn't be used in the general case (but is fine
    // if one is already make those checks before calling in to the rounding).
    public static int fastRound(final double d) {
        return (d > 0.0) ? (int) (d + 0.5d) : (int) (d - 0.5d);
    }

    public static double approximateLog10SumLog10(final double[] vals) {
        return approximateLog10SumLog10(vals, vals.length);
    }

    public static double approximateLog10SumLog10(final double[] vals, final int endIndex) {

        final int maxElementIndex = MathUtils.maxElementIndex(vals, endIndex);
        double approxSum = vals[maxElementIndex];

        for (int i = 0; i < endIndex; i++) {
            if (i == maxElementIndex || vals[i] == Double.NEGATIVE_INFINITY)
                continue;

            final double diff = approxSum - vals[i];
            if (diff < MathUtils.MAX_JACOBIAN_TOLERANCE) {
                // See notes from the 2-inout implementation below
                final int ind = fastRound(diff * MathUtils.JACOBIAN_LOG_TABLE_INV_STEP); // hard rounding
                approxSum += MathUtils.jacobianLogTable[ind];
            }
        }

        return approxSum;
    }

    public static double approximateLog10SumLog10(final double a, final double b, final double c) {
        return approximateLog10SumLog10(a, approximateLog10SumLog10(b, c));
    }

    public static double approximateLog10SumLog10(double small, double big) {
        // make sure small is really the smaller value
        if (small > big) {
            final double t = big;
            big = small;
            small = t;
        }

        if (small == Double.NEGATIVE_INFINITY || big == Double.NEGATIVE_INFINITY)
            return big;

        final double diff = big - small;
        if (diff >= MathUtils.MAX_JACOBIAN_TOLERANCE)
            return big;

        // OK, so |y-x| < tol: we use the following identity then:
        // we need to compute log10(10^x + 10^y)
        // By Jacobian logarithm identity, this is equal to
        // max(x,y) + log10(1+10^-abs(x-y))
        // we compute the second term as a table lookup with integer quantization
        // we have pre-stored correction for 0,0.1,0.2,... 10.0
        final int ind = fastRound(diff * MathUtils.JACOBIAN_LOG_TABLE_INV_STEP); // hard rounding
        return big + MathUtils.jacobianLogTable[ind];
    }

    public static double sum(final double[] values) {
        double s = 0.0;
        for (double v : values)
            s += v;
        return s;
    }

    public static long sum(final int[] x) {
        long total = 0;
        for (int v : x)
            total += v;
        return total;
    }

    public static int sum(final byte[] x) {
        int total = 0;
        for (byte v : x)
            total += (int)v;
        return total;
    }

    public static double percentage(int x, int base) {
        return (base > 0 ? ((double) x / (double) base) * 100.0 : 0);
    }

    public static double ratio(final int num, final int denom) {
        if ( denom > 0 ) {
            return ((double) num)/denom;
        } else {
            if ( num == 0 && denom == 0) {
                return 0.0;
            } else {
                throw new ReviewedStingException(String.format("The denominator of a ratio cannot be zero or less than zero: %d/%d",num,denom));
            }
        }
    }

    public static double ratio(final long num, final long denom) {
        if ( denom > 0L ) {
            return ((double) num)/denom;
        } else {
            if ( num == 0L && denom == 0L ) {
                return 0.0;
            } else {
                throw new ReviewedStingException(String.format("The denominator of a ratio cannot be zero or less than zero: %d/%d",num,denom));
            }
        }
    }

    /**
     * Converts a real space array of probabilities into a log10 array
     *
     * @param prRealSpace
     * @return
     */
    public static double[] toLog10(final double[] prRealSpace) {
        double[] log10s = new double[prRealSpace.length];
        for (int i = 0; i < prRealSpace.length; i++)
            log10s[i] = Math.log10(prRealSpace[i]);
        return log10s;
    }

    public static double log10sumLog10(final double[] log10p, final int start) {
        return log10sumLog10(log10p, start, log10p.length);
    }

    public static double log10sumLog10(final double[] log10p,final int start,final int finish) {
        double sum = 0.0;

        double maxValue = arrayMax(log10p, finish);
        if(maxValue == Double.NEGATIVE_INFINITY)
            return maxValue;

        for (int i = start; i < finish; i++) {
            sum += Math.pow(10.0, log10p[i] - maxValue);
        }

        return Math.log10(sum) + maxValue;
    }

    public static double sumLog10(final double[] log10values) {
        return Math.pow(10.0, log10sumLog10(log10values));
        //        double s = 0.0;
        //        for ( double v : log10values) s += Math.pow(10.0, v);
        //        return s;
    }

    public static double log10sumLog10(final double[] log10values) {
        return log10sumLog10(log10values, 0);
    }

    public static boolean wellFormedDouble(final double val) {
        return !Double.isInfinite(val) && !Double.isNaN(val);
    }

    public static double bound(final double value, final double minBoundary, final double maxBoundary) {
        return Math.max(Math.min(value, maxBoundary), minBoundary);
    }

    public static boolean isBounded(final double val, final double lower, final double upper) {
        return val >= lower && val <= upper;
    }

    public static boolean isPositive(final double val) {
        return !isNegativeOrZero(val);
    }

    public static boolean isPositiveOrZero(final double val) {
        return isBounded(val, 0.0, Double.POSITIVE_INFINITY);
    }

    public static boolean isNegativeOrZero(final double val) {
        return isBounded(val, Double.NEGATIVE_INFINITY, 0.0);
    }

    public static boolean isNegative(final double val) {
        return !isPositiveOrZero(val);
    }

    /**
     * Compares double values for equality (within 1e-6), or inequality.
     *
     * @param a the first double value
     * @param b the second double value
     * @return -1 if a is greater than b, 0 if a is equal to be within 1e-6, 1 if b is greater than a.
     */
    public static byte compareDoubles(final double a, final double b) {
        return compareDoubles(a, b, 1e-6);
    }

    /**
     * Compares double values for equality (within epsilon), or inequality.
     *
     * @param a       the first double value
     * @param b       the second double value
     * @param epsilon the precision within which two double values will be considered equal
     * @return -1 if a is greater than b, 0 if a is equal to be within epsilon, 1 if b is greater than a.
     */
    public static byte compareDoubles(final double a, final double b, final double epsilon) {
        if (Math.abs(a - b) < epsilon) {
            return 0;
        }
        if (a > b) {
            return -1;
        }
        return 1;
    }

    public static double NormalDistribution(final double mean, final double sd, final double x) {
        double a = 1.0 / (sd * Math.sqrt(2.0 * Math.PI));
        double b = Math.exp(-1.0 * (Math.pow(x - mean, 2.0) / (2.0 * sd * sd)));
        return a * b;
    }

    /**
     * Calculates the log10 of the binomial coefficient. Designed to prevent
     * overflows even with very large numbers.
     *
     * @param n total number of trials
     * @param k number of successes
     * @return the log10 of the binomial coefficient
     */
    public static double binomialCoefficient(final int n, final int k) {
        return Math.pow(10, log10BinomialCoefficient(n, k));
    }

    /**
     * @see #binomialCoefficient(int, int) with log10 applied to result
     */
    public static double log10BinomialCoefficient(final int n, final int k) {
        return log10Factorial(n) - log10Factorial(k) - log10Factorial(n - k);
    }

    /**
     * Computes a binomial probability.  This is computed using the formula
     * <p/>
     * B(k; n; p) = [ n! / ( k! (n - k)! ) ] (p^k)( (1-p)^k )
     * <p/>
     * where n is the number of trials, k is the number of successes, and p is the probability of success
     *
     * @param n number of Bernoulli trials
     * @param k number of successes
     * @param p probability of success
     * @return the binomial probability of the specified configuration.  Computes values down to about 1e-237.
     */
    public static double binomialProbability(final int n, final int k, final double p) {
        return Math.pow(10, log10BinomialProbability(n, k, Math.log10(p)));
    }

    /**
     * @see #binomialProbability(int, int, double) with log10 applied to result
     */
    public static double log10BinomialProbability(final int n, final int k, final double log10p) {
        double log10OneMinusP = Math.log10(1 - Math.pow(10, log10p));
        return log10BinomialCoefficient(n, k) + log10p * k + log10OneMinusP * (n - k);
    }

    /**
     * @see #binomialProbability(int, int, double) with p=0.5
     */
    public static double binomialProbability(final int n, final int k) {
        return Math.pow(10, log10BinomialProbability(n, k));
    }

    /**
     * @see #binomialProbability(int, int, double) with p=0.5 and log10 applied to result
     */
    public static double log10BinomialProbability(final int n, final int k) {
        return log10BinomialCoefficient(n, k) + (n * FAIR_BINOMIAL_PROB_LOG10_0_5);
    }

    /**
     * Performs the cumulative sum of binomial probabilities, where the probability calculation is done in log space.
     * Assumes that the probability of a successful hit is fair (i.e. 0.5).
     *
     * @param n         number of attempts for the number of hits
     * @param k_start   start (inclusive) of the cumulant sum (over hits)
     * @param k_end     end (inclusive) of the cumulant sum (over hits)
     * @return - returns the cumulative probability
     */
    public static double binomialCumulativeProbability(final int n, final int k_start, final int k_end) {
        if ( k_end > n )
            throw new IllegalArgumentException(String.format("Value for k_end (%d) is greater than n (%d)", k_end, n));

        double cumProb = 0.0;
        double prevProb;
        BigDecimal probCache = BigDecimal.ZERO;

        for (int hits = k_start; hits <= k_end; hits++) {
            prevProb = cumProb;
            final double probability = binomialProbability(n, hits);
            cumProb += probability;
            if (probability > 0 && cumProb - prevProb < probability / 2) { // loss of precision
                probCache = probCache.add(new BigDecimal(prevProb));
                cumProb = 0.0;
                hits--; // repeat loop
                // prevProb changes at start of loop
            }
        }

        return probCache.add(new BigDecimal(cumProb)).doubleValue();
    }

    /**
     * Calculates the log10 of the multinomial coefficient. Designed to prevent
     * overflows even with very large numbers.
     *
     * @param n total number of trials
     * @param k array of any size with the number of successes for each grouping (k1, k2, k3, ..., km)
     * @return
     */
    public static double log10MultinomialCoefficient(final int n, final int[] k) {
        double denominator = 0.0;
        for (int x : k) {
            denominator += log10Factorial(x);
        }
        return log10Factorial(n) - denominator;
    }

    /**
     * Computes the log10 of the multinomial distribution probability given a vector
     * of log10 probabilities. Designed to prevent overflows even with very large numbers.
     *
     * @param n      number of trials
     * @param k      array of number of successes for each possibility
     * @param log10p array of log10 probabilities
     * @return
     */
    public static double log10MultinomialProbability(final int n, final int[] k, final double[] log10p) {
        if (log10p.length != k.length)
            throw new UserException.BadArgumentValue("p and k", "Array of log10 probabilities must have the same size as the array of number of sucesses: " + log10p.length + ", " + k.length);
        double log10Prod = 0.0;
        for (int i = 0; i < log10p.length; i++) {
            log10Prod += log10p[i] * k[i];
        }
        return log10MultinomialCoefficient(n, k) + log10Prod;
    }

    /**
     * Computes a multinomial coefficient efficiently avoiding overflow even for large numbers.
     * This is computed using the formula:
     * <p/>
     * M(x1,x2,...,xk; n) = [ n! / (x1! x2! ... xk!) ]
     * <p/>
     * where xi represents the number of times outcome i was observed, n is the number of total observations.
     * In this implementation, the value of n is inferred as the sum over i of xi.
     *
     * @param k an int[] of counts, where each element represents the number of times a certain outcome was observed
     * @return the multinomial of the specified configuration.
     */
    public static double multinomialCoefficient(final int[] k) {
        int n = 0;
        for (int xi : k) {
            n += xi;
        }

        return Math.pow(10, log10MultinomialCoefficient(n, k));
    }

    /**
     * Computes a multinomial probability efficiently avoiding overflow even for large numbers.
     * This is computed using the formula:
     * <p/>
     * M(x1,x2,...,xk; n; p1,p2,...,pk) = [ n! / (x1! x2! ... xk!) ] (p1^x1)(p2^x2)(...)(pk^xk)
     * <p/>
     * where xi represents the number of times outcome i was observed, n is the number of total observations, and
     * pi represents the probability of the i-th outcome to occur.  In this implementation, the value of n is
     * inferred as the sum over i of xi.
     *
     * @param k an int[] of counts, where each element represents the number of times a certain outcome was observed
     * @param p a double[] of probabilities, where each element represents the probability a given outcome can occur
     * @return the multinomial probability of the specified configuration.
     */
    public static double multinomialProbability(final int[] k, final double[] p) {
        if (p.length != k.length)
            throw new UserException.BadArgumentValue("p and k", "Array of log10 probabilities must have the same size as the array of number of sucesses: " + p.length + ", " + k.length);

        int n = 0;
        double[] log10P = new double[p.length];
        for (int i = 0; i < p.length; i++) {
            log10P[i] = Math.log10(p[i]);
            n += k[i];
        }
        return Math.pow(10, log10MultinomialProbability(n, k, log10P));
    }

    /**
     * calculate the Root Mean Square of an array of integers
     *
     * @param x an byte[] of numbers
     * @return the RMS of the specified numbers.
     */
    public static double rms(final byte[] x) {
        if (x.length == 0)
            return 0.0;

        double rms = 0.0;
        for (int i : x)
            rms += i * i;
        rms /= x.length;
        return Math.sqrt(rms);
    }

    /**
     * calculate the Root Mean Square of an array of integers
     *
     * @param x an int[] of numbers
     * @return the RMS of the specified numbers.
     */
    public static double rms(final int[] x) {
        if (x.length == 0)
            return 0.0;

        double rms = 0.0;
        for (int i : x)
            rms += i * i;
        rms /= x.length;
        return Math.sqrt(rms);
    }

    /**
     * calculate the Root Mean Square of an array of doubles
     *
     * @param x a double[] of numbers
     * @return the RMS of the specified numbers.
     */
    public static double rms(final Double[] x) {
        if (x.length == 0)
            return 0.0;

        double rms = 0.0;
        for (Double i : x)
            rms += i * i;
        rms /= x.length;
        return Math.sqrt(rms);
    }

    public static double rms(final Collection<Integer> l) {
        if (l.size() == 0)
            return 0.0;

        double rms = 0.0;
        for (int i : l)
            rms += i * i;
        rms /= l.size();
        return Math.sqrt(rms);
    }

    public static double distanceSquared(final double[] x, final double[] y) {
        double dist = 0.0;
        for (int iii = 0; iii < x.length; iii++) {
            dist += (x[iii] - y[iii]) * (x[iii] - y[iii]);
        }
        return dist;
    }

    public static double round(final double num, final int digits) {
        double result = num * Math.pow(10.0, (double) digits);
        result = Math.round(result);
        result = result / Math.pow(10.0, (double) digits);
        return result;
    }

    /**
     * normalizes the log10-based array.  ASSUMES THAT ALL ARRAY ENTRIES ARE <= 0 (<= 1 IN REAL-SPACE).
     *
     * @param array             the array to be normalized
     * @param takeLog10OfOutput if true, the output will be transformed back into log10 units
     * @return a newly allocated array corresponding the normalized values in array, maybe log10 transformed
     */
    public static double[] normalizeFromLog10(final double[] array, final boolean takeLog10OfOutput) {
        return normalizeFromLog10(array, takeLog10OfOutput, false);
    }

    /**
     * See #normalizeFromLog10 but with the additional option to use an approximation that keeps the calculation always in log-space
     *
     * @param array
     * @param takeLog10OfOutput
     * @param keepInLogSpace
     *
     * @return
     */
    public static double[] normalizeFromLog10(final double[] array, final boolean takeLog10OfOutput, final boolean keepInLogSpace) {
        // for precision purposes, we need to add (or really subtract, since they're
        // all negative) the largest value; also, we need to convert to normal-space.
        double maxValue = arrayMax(array);

        // we may decide to just normalize in log space without converting to linear space
        if (keepInLogSpace) {
            for (int i = 0; i < array.length; i++) {
                array[i] -= maxValue;
            }
            return array;
        }

        // default case: go to linear space
        double[] normalized = new double[array.length];

        for (int i = 0; i < array.length; i++)
            normalized[i] = Math.pow(10, array[i] - maxValue);

        // normalize
        double sum = 0.0;
        for (int i = 0; i < array.length; i++)
            sum += normalized[i];
        for (int i = 0; i < array.length; i++) {
            double x = normalized[i] / sum;
            if (takeLog10OfOutput) {
                x = Math.log10(x);
                if ( x < LOG10_P_OF_ZERO || Double.isInfinite(x) )
                    x = array[i] - maxValue;
            }

            normalized[i] = x;
        }

        return normalized;
    }

    /**
     * normalizes the log10-based array.  ASSUMES THAT ALL ARRAY ENTRIES ARE <= 0 (<= 1 IN REAL-SPACE).
     *
     * @param array the array to be normalized
     * @return a newly allocated array corresponding the normalized values in array
     */
    public static double[] normalizeFromLog10(final double[] array) {
        return normalizeFromLog10(array, false);
    }

    /**
     * normalizes the real-space probability array.
     *
     * Does not assume anything about the values in the array, beyond that no elements are below 0.  It's ok
     * to have values in the array of > 1, or have the sum go above 0.
     *
     * @param array the array to be normalized
     * @return a newly allocated array corresponding the normalized values in array
     */
    @Requires("array != null")
    @Ensures({"result != null"})
    public static double[] normalizeFromRealSpace(final double[] array) {
        if ( array.length == 0 )
            return array;

        final double sum = sum(array);
        final double[] normalized = new double[array.length];
        if ( sum < 0.0 ) throw new IllegalArgumentException("Values in probability array sum to a negative number " + sum);
        for ( int i = 0; i < array.length; i++ ) {
            normalized[i] = array[i] / sum;
        }
        return normalized;
    }

    public static int maxElementIndex(final double[] array) {
        return maxElementIndex(array, array.length);
    }

    public static int maxElementIndex(final double[] array, final int endIndex) {
        if (array == null || array.length == 0)
            throw new IllegalArgumentException("Array cannot be null!");

        int maxI = 0;
        for (int i = 1; i < endIndex; i++) {
            if (array[i] > array[maxI])
                maxI = i;
        }

        return maxI;
    }

    public static int maxElementIndex(final int[] array) {
        return maxElementIndex(array, array.length);
    }

    public static int maxElementIndex(final byte[] array) {
        return maxElementIndex(array, array.length);
    }

    public static int maxElementIndex(final int[] array, final int endIndex) {
        if (array == null || array.length == 0)
            throw new IllegalArgumentException("Array cannot be null!");

        int maxI = 0;
        for (int i = 1; i < endIndex; i++) {
            if (array[i] > array[maxI])
                maxI = i;
        }

        return maxI;
    }

    public static int maxElementIndex(final byte[] array, final int endIndex) {
        if (array == null || array.length == 0)
            throw new IllegalArgumentException("Array cannot be null!");

        int maxI = 0;
        for (int i = 1; i < endIndex; i++) {
            if (array[i] > array[maxI])
                maxI = i;
        }

        return maxI;
    }

    public static int arrayMax(final int[] array) {
        return array[maxElementIndex(array)];
    }


    public static double arrayMax(final double[] array) {
        return array[maxElementIndex(array)];
    }

    public static double arrayMax(final double[] array, final int endIndex) {
        return array[maxElementIndex(array, endIndex)];
    }

    public static double arrayMin(final double[] array) {
        return array[minElementIndex(array)];
    }

    public static int arrayMin(final int[] array) {
        return array[minElementIndex(array)];
    }

    public static byte arrayMin(final byte[] array) {
        return array[minElementIndex(array)];
    }

    public static int minElementIndex(final double[] array) {
        if (array == null || array.length == 0)
            throw new IllegalArgumentException("Array cannot be null!");

        int minI = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] < array[minI])
                minI = i;
        }

        return minI;
    }

    public static int minElementIndex(final byte[] array) {
        if (array == null || array.length == 0)
            throw new IllegalArgumentException("Array cannot be null!");

        int minI = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] < array[minI])
                minI = i;
        }

        return minI;
    }

    public static int minElementIndex(final int[] array) {
        if (array == null || array.length == 0)
            throw new IllegalArgumentException("Array cannot be null!");

        int minI = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] < array[minI])
                minI = i;
        }

        return minI;
    }

    public static int arrayMaxInt(final List<Integer> array) {
        if (array == null)
            throw new IllegalArgumentException("Array cannot be null!");
        if (array.size() == 0)
            throw new IllegalArgumentException("Array size cannot be 0!");

        int m = array.get(0);
        for (int e : array)
            m = Math.max(m, e);
        return m;
    }

    public static int sum(final List<Integer> list ) {
        int sum = 0;
        for ( Integer i : list ) {
          sum += i;
        }
        return sum;
    }

    public static double average(final List<Long> vals, final int maxI) {
        long sum = 0L;

        int i = 0;
        for (long x : vals) {
            if (i > maxI)
                break;
            sum += x;
            i++;
            //System.out.printf(" %d/%d", sum, i);
        }

        //System.out.printf("Sum = %d, n = %d, maxI = %d, avg = %f%n", sum, i, maxI, (1.0 * sum) / i);

        return (1.0 * sum) / i;
    }

    public static double average(final List<Long> vals) {
        return average(vals, vals.size());
    }

    public static int countOccurrences(final char c, final String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            count += s.charAt(i) == c ? 1 : 0;
        }
        return count;
    }

    public static <T> int countOccurrences(T x, List<T> l) {
        int count = 0;
        for (T y : l) {
            if (x.equals(y))
                count++;
        }

        return count;
    }

    public static int countOccurrences(byte element, byte[] array) {
        int count = 0;
        for (byte y : array) {
            if (element == y)
                count++;
        }

        return count;
    }


    /**
     * Returns n random indices drawn with replacement from the range 0..(k-1)
     *
     * @param n the total number of indices sampled from
     * @param k the number of random indices to draw (with replacement)
     * @return a list of k random indices ranging from 0 to (n-1) with possible duplicates
     */
    static public ArrayList<Integer> sampleIndicesWithReplacement(final int n, final int k) {

        ArrayList<Integer> chosen_balls = new ArrayList<Integer>(k);
        for (int i = 0; i < k; i++) {
            //Integer chosen_ball = balls[rand.nextInt(k)];
            chosen_balls.add(GenomeAnalysisEngine.getRandomGenerator().nextInt(n));
            //balls.remove(chosen_ball);
        }

        return chosen_balls;
    }

    /**
     * Returns n random indices drawn without replacement from the range 0..(k-1)
     *
     * @param n the total number of indices sampled from
     * @param k the number of random indices to draw (without replacement)
     * @return a list of k random indices ranging from 0 to (n-1) without duplicates
     */
    static public ArrayList<Integer> sampleIndicesWithoutReplacement(final int n, final int k) {
        ArrayList<Integer> chosen_balls = new ArrayList<Integer>(k);

        for (int i = 0; i < n; i++) {
            chosen_balls.add(i);
        }

        Collections.shuffle(chosen_balls, GenomeAnalysisEngine.getRandomGenerator());

        //return (ArrayList<Integer>) chosen_balls.subList(0, k);
        return new ArrayList<Integer>(chosen_balls.subList(0, k));
    }

    /**
     * Given a list of indices into a list, return those elements of the list with the possibility of drawing list elements multiple times
     *
     * @param indices the list of indices for elements to extract
     * @param list    the list from which the elements should be extracted
     * @param <T>     the template type of the ArrayList
     * @return a new ArrayList consisting of the elements at the specified indices
     */
    static public <T> ArrayList<T> sliceListByIndices(final List<Integer> indices, final List<T> list) {
        ArrayList<T> subset = new ArrayList<T>();

        for (int i : indices) {
            subset.add(list.get(i));
        }

        return subset;
    }

    /**
     * Given two log-probability vectors, compute log of vector product of them:
     * in Matlab notation, return log10(10.*x'*10.^y)
     * @param x vector 1
     * @param y vector 2
     * @return a double representing log (dotProd(10.^x,10.^y)
     */
    public static double logDotProduct(final double [] x, final double[] y) {
        if (x.length != y.length)
            throw new ReviewedStingException("BUG: Vectors of different lengths");

        double tmpVec[] = new double[x.length];

        for (int k=0; k < tmpVec.length; k++ ) {
            tmpVec[k] = x[k]+y[k];
        }

        return log10sumLog10(tmpVec);



    }

    /**
     * Check that the log10 prob vector vector is well formed
     *
     * @param vector
     * @param expectedSize
     * @param shouldSumToOne
     *
     * @return true if vector is well-formed, false otherwise
     */
    public static boolean goodLog10ProbVector(final double[] vector, final int expectedSize, final boolean shouldSumToOne) {
        if ( vector.length != expectedSize ) return false;

        for ( final double pr : vector ) {
            if ( ! goodLog10Probability(pr) )
                return false;
        }

        if ( shouldSumToOne && compareDoubles(sumLog10(vector), 1.0, 1e-4) != 0 )
            return false;

        return true; // everything is good
    }

    /**
     * Checks that the result is a well-formed log10 probability
     *
     * @param result a supposedly well-formed log10 probability value.  By default allows
     *               -Infinity values, as log10(0.0) == -Infinity.
     * @return true if result is really well formed
     */
    public static boolean goodLog10Probability(final double result) {
        return goodLog10Probability(result, true);
    }

    /**
     * Checks that the result is a well-formed log10 probability
     *
     * @param result a supposedly well-formed log10 probability value
     * @param allowNegativeInfinity should we consider a -Infinity value ok?
     * @return true if result is really well formed
     */
    public static boolean goodLog10Probability(final double result, final boolean allowNegativeInfinity) {
        return result <= 0.0 && result != Double.POSITIVE_INFINITY && (allowNegativeInfinity || result != Double.NEGATIVE_INFINITY) && ! Double.isNaN(result);
    }

    /**
     * Checks that the result is a well-formed probability
     *
     * @param result a supposedly well-formed probability value
     * @return true if result is really well formed
     */
    public static boolean goodProbability(final double result) {
        return result >= 0.0 && result <= 1.0 && ! Double.isInfinite(result) && ! Double.isNaN(result);
    }

    /**
     * A utility class that computes on the fly average and standard deviation for a stream of numbers.
     * The number of observations does not have to be known in advance, and can be also very big (so that
     * it could overflow any naive summation-based scheme or cause loss of precision).
     * Instead, adding a new number <code>observed</code>
     * to a sample with <code>add(observed)</code> immediately updates the instance of this object so that
     * it contains correct mean and standard deviation for all the numbers seen so far. Source: Knuth, vol.2
     * (see also e.g. http://www.johndcook.com/standard_deviation.html for online reference).
     */
    public static class RunningAverage {
        private double mean = 0.0;
        private double s = 0.0;
        private long obs_count = 0;

        public void add(double obs) {
            obs_count++;
            double oldMean = mean;
            mean += (obs - mean) / obs_count; // update mean
            s += (obs - oldMean) * (obs - mean);
        }

        public void addAll(Collection<Number> col) {
            for (Number o : col) {
                add(o.doubleValue());
            }
        }

        public double mean() {
            return mean;
        }

        public double stddev() {
            return Math.sqrt(s / (obs_count - 1));
        }

        public double var() {
            return s / (obs_count - 1);
        }

        public long observationCount() {
            return obs_count;
        }

        public RunningAverage clone() {
            RunningAverage ra = new RunningAverage();
            ra.mean = this.mean;
            ra.s = this.s;
            ra.obs_count = this.obs_count;
            return ra;
        }

        public void merge(RunningAverage other) {
            if (this.obs_count > 0 || other.obs_count > 0) { // if we have any observations at all
                this.mean = (this.mean * this.obs_count + other.mean * other.obs_count) / (this.obs_count + other.obs_count);
                this.s += other.s;
            }
            this.obs_count += other.obs_count;
        }
    }

    //
    // useful common utility routines
    //

    static public double max(double x0, double x1, double x2) {
        double a = Math.max(x0, x1);
        return Math.max(a, x2);
    }

    /**
     * Converts LN to LOG10
     *
     * @param ln log(x)
     * @return log10(x)
     */
    public static double lnToLog10(final double ln) {
        return ln * Math.log10(Math.E);
    }

    /**
     * Constants to simplify the log gamma function calculation.
     */
    private static final double zero = 0.0, one = 1.0, half = .5, a0 = 7.72156649015328655494e-02, a1 = 3.22467033424113591611e-01, a2 = 6.73523010531292681824e-02, a3 = 2.05808084325167332806e-02, a4 = 7.38555086081402883957e-03, a5 = 2.89051383673415629091e-03, a6 = 1.19270763183362067845e-03, a7 = 5.10069792153511336608e-04, a8 = 2.20862790713908385557e-04, a9 = 1.08011567247583939954e-04, a10 = 2.52144565451257326939e-05, a11 = 4.48640949618915160150e-05, tc = 1.46163214496836224576e+00, tf = -1.21486290535849611461e-01, tt = -3.63867699703950536541e-18, t0 = 4.83836122723810047042e-01, t1 = -1.47587722994593911752e-01, t2 = 6.46249402391333854778e-02, t3 = -3.27885410759859649565e-02, t4 = 1.79706750811820387126e-02, t5 = -1.03142241298341437450e-02, t6 = 6.10053870246291332635e-03, t7 = -3.68452016781138256760e-03, t8 = 2.25964780900612472250e-03, t9 = -1.40346469989232843813e-03, t10 = 8.81081882437654011382e-04, t11 = -5.38595305356740546715e-04, t12 = 3.15632070903625950361e-04, t13 = -3.12754168375120860518e-04, t14 = 3.35529192635519073543e-04, u0 = -7.72156649015328655494e-02, u1 = 6.32827064025093366517e-01, u2 = 1.45492250137234768737e+00, u3 = 9.77717527963372745603e-01, u4 = 2.28963728064692451092e-01, u5 = 1.33810918536787660377e-02, v1 = 2.45597793713041134822e+00, v2 = 2.12848976379893395361e+00, v3 = 7.69285150456672783825e-01, v4 = 1.04222645593369134254e-01, v5 = 3.21709242282423911810e-03, s0 = -7.72156649015328655494e-02, s1 = 2.14982415960608852501e-01, s2 = 3.25778796408930981787e-01, s3 = 1.46350472652464452805e-01, s4 = 2.66422703033638609560e-02, s5 = 1.84028451407337715652e-03, s6 = 3.19475326584100867617e-05, r1 = 1.39200533467621045958e+00, r2 = 7.21935547567138069525e-01, r3 = 1.71933865632803078993e-01, r4 = 1.86459191715652901344e-02, r5 = 7.77942496381893596434e-04, r6 = 7.32668430744625636189e-06, w0 = 4.18938533204672725052e-01, w1 = 8.33333333333329678849e-02, w2 = -2.77777777728775536470e-03, w3 = 7.93650558643019558500e-04, w4 = -5.95187557450339963135e-04, w5 = 8.36339918996282139126e-04, w6 = -1.63092934096575273989e-03;

    /**
     * Efficient rounding functions to simplify the log gamma function calculation
     * double to long with 32 bit shift
     */
    private static final int HI(final double x) {
        return (int) (Double.doubleToLongBits(x) >> 32);
    }

    /**
     * Efficient rounding functions to simplify the log gamma function calculation
     * double to long without shift
     */
    private static final int LO(final double x) {
        return (int) Double.doubleToLongBits(x);
    }

    /**
     * Most efficent implementation of the lnGamma (FDLIBM)
     * Use via the log10Gamma wrapper method.
     */
    private static double lnGamma(final double x) {
        double t, y, z, p, p1, p2, p3, q, r, w;
        int i;

        int hx = HI(x);
        int lx = LO(x);

        /* purge off +-inf, NaN, +-0, and negative arguments */
        int ix = hx & 0x7fffffff;
        if (ix >= 0x7ff00000)
            return Double.POSITIVE_INFINITY;
        if ((ix | lx) == 0 || hx < 0)
            return Double.NaN;
        if (ix < 0x3b900000) {    /* |x|<2**-70, return -log(|x|) */
            return -Math.log(x);
        }

        /* purge off 1 and 2 */
        if ((((ix - 0x3ff00000) | lx) == 0) || (((ix - 0x40000000) | lx) == 0))
            r = 0;
            /* for x < 2.0 */
        else if (ix < 0x40000000) {
            if (ix <= 0x3feccccc) {     /* lgamma(x) = lgamma(x+1)-log(x) */
                r = -Math.log(x);
                if (ix >= 0x3FE76944) {
                    y = one - x;
                    i = 0;
                }
                else if (ix >= 0x3FCDA661) {
                    y = x - (tc - one);
                    i = 1;
                }
                else {
                    y = x;
                    i = 2;
                }
            }
            else {
                r = zero;
                if (ix >= 0x3FFBB4C3) {
                    y = 2.0 - x;
                    i = 0;
                } /* [1.7316,2] */
                else if (ix >= 0x3FF3B4C4) {
                    y = x - tc;
                    i = 1;
                } /* [1.23,1.73] */
                else {
                    y = x - one;
                    i = 2;
                }
            }

            switch (i) {
                case 0:
                    z = y * y;
                    p1 = a0 + z * (a2 + z * (a4 + z * (a6 + z * (a8 + z * a10))));
                    p2 = z * (a1 + z * (a3 + z * (a5 + z * (a7 + z * (a9 + z * a11)))));
                    p = y * p1 + p2;
                    r += (p - 0.5 * y);
                    break;
                case 1:
                    z = y * y;
                    w = z * y;
                    p1 = t0 + w * (t3 + w * (t6 + w * (t9 + w * t12)));    /* parallel comp */
                    p2 = t1 + w * (t4 + w * (t7 + w * (t10 + w * t13)));
                    p3 = t2 + w * (t5 + w * (t8 + w * (t11 + w * t14)));
                    p = z * p1 - (tt - w * (p2 + y * p3));
                    r += (tf + p);
                    break;
                case 2:
                    p1 = y * (u0 + y * (u1 + y * (u2 + y * (u3 + y * (u4 + y * u5)))));
                    p2 = one + y * (v1 + y * (v2 + y * (v3 + y * (v4 + y * v5))));
                    r += (-0.5 * y + p1 / p2);
            }
        }
        else if (ix < 0x40200000) {             /* x < 8.0 */
            i = (int) x;
            t = zero;
            y = x - (double) i;
            p = y * (s0 + y * (s1 + y * (s2 + y * (s3 + y * (s4 + y * (s5 + y * s6))))));
            q = one + y * (r1 + y * (r2 + y * (r3 + y * (r4 + y * (r5 + y * r6)))));
            r = half * y + p / q;
            z = one;    /* lgamma(1+s) = log(s) + lgamma(s) */
            switch (i) {
                case 7:
                    z *= (y + 6.0);    /* FALLTHRU */
                case 6:
                    z *= (y + 5.0);    /* FALLTHRU */
                case 5:
                    z *= (y + 4.0);    /* FALLTHRU */
                case 4:
                    z *= (y + 3.0);    /* FALLTHRU */
                case 3:
                    z *= (y + 2.0);    /* FALLTHRU */
                    r += Math.log(z);
                    break;
            }
            /* 8.0 <= x < 2**58 */
        }
        else if (ix < 0x43900000) {
            t = Math.log(x);
            z = one / x;
            y = z * z;
            w = w0 + z * (w1 + y * (w2 + y * (w3 + y * (w4 + y * (w5 + y * w6)))));
            r = (x - half) * (t - one) + w;
        }
        else
            /* 2**58 <= x <= inf */
            r = x * (Math.log(x) - one);
        return r;
    }

    /**
     * Calculates the log10 of the gamma function for x using the efficient FDLIBM
     * implementation to avoid overflows and guarantees high accuracy even for large
     * numbers.
     *
     * @param x the x parameter
     * @return the log10 of the gamma function at x.
     */
    public static double log10Gamma(final double x) {
        return lnToLog10(lnGamma(x));
    }

    public static double factorial(final int x) {
        // avoid rounding errors caused by fact that 10^log(x) might be slightly lower than x and flooring may produce 1 less than real value
        return (double)Math.round(Math.pow(10, log10Factorial(x)));
    }

    public static double log10Factorial(final int x) {
        if (x >= log10FactorialCache.length || x < 0)
            return log10Gamma(x + 1);
        else
            return log10FactorialCache[x];
    }

    /**
     * Adds two arrays together and returns a new array with the sum.
     *
     * @param a one array
     * @param b another array
     * @return a new array with the sum of a and b
     */
    @Requires("a.length == b.length")
    @Ensures("result.length == a.length")
    public static int[] addArrays(final int[] a, final int[] b) {
        int[] c = new int[a.length];
        for (int i = 0; i < a.length; i++)
            c[i] = a[i] + b[i];
        return c;
    }

    /** Same routine, unboxed types for efficiency
     *
     * @param x                 First vector
     * @param y                 Second vector
     * @return Vector of same length as x and y so that z[k] = x[k]+y[k]
     */
    public static double[] vectorSum(final double[]x, final double[] y) {
        if (x.length != y.length)
            throw new ReviewedStingException("BUG: Lengths of x and y must be the same");

        double[] result = new double[x.length];
        for (int k=0; k <x.length; k++)
            result[k] = x[k]+y[k];

        return result;
    }

    /** Compute Z=X-Y for two numeric vectors X and Y
     *
     * @param x                 First vector
     * @param y                 Second vector
     * @return Vector of same length as x and y so that z[k] = x[k]-y[k]
     */
    public static int[] vectorDiff(final int[]x, final int[] y) {
        if (x.length != y.length)
            throw new ReviewedStingException("BUG: Lengths of x and y must be the same");

        int[] result = new int[x.length];
        for (int k=0; k <x.length; k++)
            result[k] = x[k]-y[k];

        return result;
    }

    /**
     * Returns a series of integer values between start and stop, inclusive,
     * expontentially distributed between the two.  That is, if there are
     * ten values between 0-10 there will be 10 between 10-100.
     *
     * WARNING -- BADLY TESTED
     * @param start
     * @param stop
     * @param eps
     * @return
     */
    public static List<Integer> log10LinearRange(final int start, final int stop, final double eps) {
        final LinkedList<Integer> values = new LinkedList<Integer>();
        final double log10range = Math.log10(stop - start);

        if ( start == 0 )
            values.add(0);

        double i = 0.0;
        while ( i <= log10range ) {
            final int index = (int)Math.round(Math.pow(10, i)) + start;
            if ( index < stop && (values.peekLast() == null || values.peekLast() != index ) )
                values.add(index);
            i += eps;
        }

        if ( values.peekLast() == null || values.peekLast() != stop )
            values.add(stop);

        return values;
    }

    /**
     * Compute in a numerical correct way the quanity log10(1-x)
     *
     * Uses the approximation log10(1-x) = log10(1/x - 1) + log10(x) to avoid very quick underflow
     * in 1-x when x is very small
     *
     * @param x a positive double value between 0.0 and 1.0
     * @return an estimate of log10(1-x)
     */
    @Requires("x >= 0.0 && x <= 1.0")
    @Ensures("result <= 0.0")
    public static double log10OneMinusX(final double x) {
        if ( x == 1.0 )
            return Double.NEGATIVE_INFINITY;
        else if ( x == 0.0 )
            return 0.0;
        else {
            final double d = Math.log10(1 / x - 1) + Math.log10(x);
            return Double.isInfinite(d) || d > 0.0 ? 0.0 : d;
        }
    }

    /**
     * Draw N random elements from list
     * @param list - the list from which to draw randomly
     * @param N - the number of elements to draw
     */
    public static <T> List<T> randomSubset(final List<T> list, final int N) {
        if (list.size() <= N) {
            return list;
        }

        return sliceListByIndices(sampleIndicesWithoutReplacement(list.size(),N),list);
    }

}
