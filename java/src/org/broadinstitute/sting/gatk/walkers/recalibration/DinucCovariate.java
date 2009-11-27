package org.broadinstitute.sting.gatk.walkers.recalibration;

import java.util.HashMap;

import org.broadinstitute.sting.utils.BaseUtils;

/*
 * Copyright (c) 2009 The Broad Institute
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
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

/**
 * Created by IntelliJ IDEA.
 * User: rpoplin
 * Date: Nov 3, 2009
 *
 * The Dinucleotide covariate. This base and the one that came before it in the read, remembering to swap directions if negative strand read.
 * This covariate assumes that the bases have been swapped to their complement base counterpart if this is a negative strand read.
 * This assumption is made to speed up the code.
 */

public class DinucCovariate implements Covariate {

    HashMap<Integer, Dinuc> dinucHashMap;

    // Initialize any member variables using the command-line arguments passed to the walkers
    public void initialize( final RecalibrationArgumentCollection RAC ) {
        final byte[] BASES = { (byte)'A', (byte)'C', (byte)'G', (byte)'T' };
        dinucHashMap = new HashMap<Integer, Dinuc>();
        for(byte byte1 : BASES) {
            for(byte byte2: BASES) {
                dinucHashMap.put( Dinuc.hashBytes(byte1, byte2), new Dinuc(byte1, byte2) ); // This might seem silly, but Strings are too slow
            }
        }
    }

    // Used to pick out the covariate's value from attributes of the read
    public final Comparable getValue( final ReadHashDatum readDatum, final int offset ) {

        byte base;
        byte prevBase;
        // If this is a negative strand read then we need to reverse the direction for our previous base
        if( readDatum.isNegStrand ) {
            base = (byte)BaseUtils.simpleComplement( (char)readDatum.bases[offset] );
            prevBase = (byte)BaseUtils.simpleComplement( (char)readDatum.bases[offset + 1] );
        } else {
            base = readDatum.bases[offset];
            prevBase = readDatum.bases[offset - 1];
        }
        //char[] charArray = {(char)prevBase, (char)base};
        //return new String( charArray ); // This is an expensive call
        return dinucHashMap.get( Dinuc.hashBytes( prevBase, base ) );
        //return String.format("%c%c", prevBase, base); // This return statement is too slow
    }

    // Used to get the covariate's value from input csv file in TableRecalibrationWalker
    public final Comparable getValue( final String str ) {
        //return str;
        return dinucHashMap.get( Dinuc.hashBytes( (byte)str.charAt(0), (byte)str.charAt(1) ) );
    }

    // Used to estimate the amount space required for the full data HashMap
    public final int estimatedNumberOfBins() {
        return 16;
    }
}