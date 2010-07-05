/*
 * Copyright (c) 2010 The Broad Institute
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

package org.broadinstitute.sting.gatk.walkers.variantutils;

import org.broad.tribble.vcf.*;
import org.broadinstitute.sting.gatk.contexts.AlignmentContext;
import org.broadinstitute.sting.gatk.contexts.ReferenceContext;
import org.broadinstitute.sting.gatk.contexts.variantcontext.VariantContext;
import org.broadinstitute.sting.gatk.contexts.variantcontext.VariantContextUtils;
import org.broadinstitute.sting.gatk.refdata.RefMetaDataTracker;
import org.broadinstitute.sting.gatk.walkers.Requires;
import org.broadinstitute.sting.gatk.walkers.RodWalker;
import org.broadinstitute.sting.utils.SampleUtils;
import org.broadinstitute.sting.utils.StingException;
import org.broadinstitute.sting.commandline.Argument;
import org.broadinstitute.sting.utils.genotype.vcf.*;

import java.util.*;

/**
 * Combines VCF records from different sources; supports both full merges and set unions.
 * Merge: combines multiple records into a single one; if sample names overlap then they are uniquified.
 * Union: assumes each rod represents the same set of samples (although this is not enforced); using the
 *   priority list (if provided), emits a single record instance at every position represented in the rods.
 */
@Requires(value={})
public class CombineVariants extends RodWalker<Integer, Integer> {
    // the types of combinations we currently allow
    public enum ComboType { UNION, MERGE }
    @Argument(fullName="combination_type", shortName="type", doc="combination type; MERGE are supported", required=true)
    protected ComboType COMBO_TYPE;

    @Argument(fullName="rod_priority_list", shortName="priority", doc="When taking the union of variants containing genotypes: a comma-separated string describing the priority ordering for the genotypes as far as which record gets emitted; a complete priority list MUST be provided", required=true)
    protected String PRIORITY_STRING = null;

    @Argument(fullName="printComplexMerges", shortName="printComplexMerges", doc="Print out interesting sites requiring complex compatibility merging", required=false)
    protected boolean printComplexMerges = false;

    private VCFWriter vcfWriter = null;
    private List<String> priority = null;
    protected EnumSet<VariantContextUtils.MergeType> mergeOptions;

    protected final static EnumSet<VariantContextUtils.MergeType> mergeTypeOptions = EnumSet.of(VariantContextUtils.MergeType.UNION_VARIANTS, VariantContextUtils.MergeType.UNIQUIFY_GENOTYPES);
    protected final static EnumSet<VariantContextUtils.MergeType> unionTypeOptions = EnumSet.of(VariantContextUtils.MergeType.UNION_VARIANTS, VariantContextUtils.MergeType.PRIORITIZE_GENOTYPES);

    public void initialize() {

        //Set<VCFHeaderLine> hInfo = new HashSet<VCFHeaderLine>();
        //hInfo.addAll(VCFUtils.getHeaderFields(getToolkit()));

        vcfWriter = new VCFWriter(out, true);
        priority = new ArrayList<String>(Arrays.asList(PRIORITY_STRING.split(",")));


        // todo -- need to merge headers in an intelligent way

        validateAnnotateUnionArguments(priority);
        mergeOptions = COMBO_TYPE == ComboType.MERGE ? mergeTypeOptions : unionTypeOptions;
        Map<String, VCFHeader> vcfRods = SampleUtils.getRodsWithVCFHeader(getToolkit(), null);
        Set<String> samples = getSampleList(vcfRods, mergeOptions);

        Set<VCFHeaderLine> headerLines = smartMergeHeaders(vcfRods.values());
        headerLines.add(new VCFHeaderLine("source", "CombineVariants"));
        vcfWriter.writeHeader(new VCFHeader(headerLines, samples));
    }

    // todo -- Eric, where's a better place to put this?
    public static Set<String> getSampleList(Map<String, VCFHeader> headers, EnumSet<VariantContextUtils.MergeType> mergeOptions ) {
        Set<String> samples = new TreeSet<String>();
        for ( Map.Entry<String, VCFHeader> val : headers.entrySet() ) {
            VCFHeader header = val.getValue();
            for ( String sample : header.getGenotypeSamples() ) {
                samples.add(VariantContextUtils.mergedSampleName(val.getKey(), sample, mergeOptions.contains(VariantContextUtils.MergeType.UNIQUIFY_GENOTYPES)));
            }
        }

        return samples;
    }

    // todo -- Eric, where's a better place to put this?
    public static Set<VCFHeaderLine> smartMergeHeaders(Collection<VCFHeader> headers) throws IllegalStateException {
        HashMap<String, VCFHeaderLine> map = new HashMap<String, VCFHeaderLine>(); // from KEY.NAME -> line
        HashSet<VCFHeaderLine> lines = new HashSet<VCFHeaderLine>();

        for ( VCFHeader source : headers ) {
            //System.out.printf("Merging in header %s%n", source);
            for ( VCFHeaderLine line : source.getMetaData()) {
                String key = line.getKey();
                if ( line instanceof VCFNamedHeaderLine ) key = key + "." + ((VCFNamedHeaderLine) line).getName();

                if ( map.containsKey(key) ) {
                    VCFHeaderLine other = map.get(key);
                    if ( line.equals(other) )
                        continue;
//                        System.out.printf("equals duplicate key %s%n", line);
                    else if ( ! line.getClass().equals(other.getClass()) )
                        throw new IllegalStateException("Incompatible header types: " + line + " " + other );
                    else if ( line instanceof VCFFilterHeaderLine ) {
                        String lineName = ((VCFFilterHeaderLine) line).getName();
                        String otherName = ((VCFFilterHeaderLine) other).getName();
                        if ( ! lineName.equals(otherName) )
                            throw new IllegalStateException("Incompatible header types: " + line + " " + other );
                    } else {
                        String lineName = ((VCFCompoundHeaderLine) line).getName();
                        String otherName = ((VCFCompoundHeaderLine) other).getName();

                        // if the names are the same, but the values are different, we need to quit
                        if (lineName.equals(otherName) && !line.equals(other))
                            throw new IllegalStateException("Incompatible header types, collision between these two types: " + line + " " + other );
                    }
                } else {
                    map.put(key, line);
                    //System.out.printf("Adding header line %s%n", line);
                }
            }
        }

        return new HashSet<VCFHeaderLine>(map.values());
    }


    private void validateAnnotateUnionArguments(List<String> priority) {
        Set<String> rodNames = SampleUtils.getRodsNamesWithVCFHeader(getToolkit(), null);
        if ( priority == null || rodNames.size() != priority.size() )
            throw new StingException("A complete priority list must be provided when annotateUnion is provided");

        if ( ! rodNames.containsAll(rodNames) )
            throw new StingException("Not all priority elements provided as input RODs: " + PRIORITY_STRING);
    }

    public Integer map(RefMetaDataTracker tracker, ReferenceContext ref, AlignmentContext context) {
        if ( tracker == null ) // RodWalkers can make funky map calls
            return 0;

        // get all of the vcf rods at this locus
        Collection<VariantContext> vcs = tracker.getAllVariantContexts(ref, context.getLocation());
        VariantContext mergedVC = VariantContextUtils.simpleMerge(vcs, priority, mergeOptions, true, printComplexMerges);
        if ( mergedVC != null ) // only operate at the start of events
            //if ( ! mergedVC.isMixed() ) // todo remove restriction when VCF4 writer is fixed
            vcfWriter.add(mergedVC, ref.getBases());
//            else
//                logger.info(String.format("Ignoring complex event: " + mergedVC));

        return vcs.isEmpty() ? 0 : 1;
    }

    public Integer reduceInit() {
        return 0;
    }

    public Integer reduce(Integer counter, Integer sum) {
        return counter + sum;
    }

    public void onTraversalDone(Integer sum) {
        if ( vcfWriter != null )
            vcfWriter.close();
    }
}