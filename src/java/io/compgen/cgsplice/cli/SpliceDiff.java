package io.compgen.cgsplice.cli;

import io.compgen.cgsplice.junction.JunctionCounts;
import io.compgen.cgsplice.junction.JunctionDiff;
import io.compgen.cgsplice.junction.JunctionDiffException;
import io.compgen.cgsplice.junction.JunctionDiffStats;
import io.compgen.cgsplice.junction.JunctionDiffStats.JunctionDiffSample;
import io.compgen.cgsplice.junction.JunctionKey;
import io.compgen.cgsplice.junction.JunctionStats;
import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.StringUtils;
import io.compgen.common.TabWriter;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.support.stats.StatUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Command(name="splice-diff", desc="Given [junction-count] files, find differentially spliced junctions", category="splicing", experimental=true)
public class SpliceDiff extends AbstractOutputCommand {
    private List<String> filenames;
    private Integer[] groups;
    
    private double maxEditDistance = -1;
    private int minTotalCount = -1;
    
    @UnnamedArg(name = "count_file1 count_file2...")
    public void setFilename(List<String> filenames) {
        this.filenames = filenames;
    }

    @Option(desc="Require the average edit-distance to be below {val}", name="max-edit-distance")
    public void setMaxEditDistance(double maxEditDistance) {
        this.maxEditDistance = maxEditDistance;
    }

    @Option(desc="Require more than {value} total number of reads crossing a junction", name="min-total-count")
    public void setMinTotalCount(int minTotalCount) {
        this.minTotalCount = minTotalCount;
    }

    @Option(desc="Comma-delimited list of groups in the same order as the files are given (1=control, 2=experimental, Example: --groups 1,1,1,2,2,2)", name="groups")
    public void setGroups(String value) {
        List<Integer> tmp = new ArrayList<Integer>();
        for (String s:value.split(",")) {
            tmp.add(Integer.parseInt(s));
        }
        
        groups = tmp.toArray(new Integer[tmp.size()]);
        
    }

    @Exec
    public void exec() throws IOException, JunctionDiffException {
        
        JunctionDiff juncDiff = new JunctionDiff();
        juncDiff.setMinTotalCount(minTotalCount);
        juncDiff.setMaxEditDistance(maxEditDistance);
        JunctionDiffStats jdStats = juncDiff.findJunctions(filenames, groups);
        
        if (verbose) {
            System.err.println("Samples:");
            for (JunctionDiffSample sample: jdStats.getSamples()) {
                System.err.println("  " + sample.sampleName + " [" + sample.group + "] - " + sample.filename);
            }
            System.err.println("Junctions       : "+jdStats.getTotalJunctions());
            System.err.println("Filtered        : "+jdStats.getFilteredJunctions());
            System.err.println("Valid donors    : "+jdStats.getValidDonors());
            System.err.println("Valid acceptors : "+jdStats.getValidAcceptors());
            System.err.println("Final junctions : "+jdStats.getDonorAcceptorFilteredJunctions());
        }
        
        List<Double> fdrDonorR1 = new ArrayList<Double>();
        List<Double> fdrAcceptorR1 = new ArrayList<Double>();

        if (verbose) {
            System.err.println("Calculating FDR...");
        }
        
        List<Double> pvalueDonorR1 = new ArrayList<Double>();
        List<Double> pvalueAcceptorR1 = new ArrayList<Double>();

        for (JunctionKey key: juncDiff.getJunctions().keySet()) {
            if (juncDiff.getJunctions().get(key).isValidDonor()) {
                JunctionStats stats = juncDiff.getJunctions().get(key).calcStats(groups, true);
                double pvalue = juncDiff.calcPvalue(stats.tScore, true);
                    pvalueDonorR1.add(pvalue);
            }
            if (juncDiff.getJunctions().get(key).isValidAcceptor()) {
                JunctionStats stats = juncDiff.getJunctions().get(key).calcStats(groups, false);
                double pvalue = juncDiff.calcPvalue(stats.tScore, false);
                    pvalueAcceptorR1.add(pvalue);
            }
        }
            
        double[] pvals = new double[pvalueDonorR1.size()];
        for (int i=0; i<pvals.length; i++) {
            pvals[i] = pvalueDonorR1.get(i);
        }
        double[] fdr = StatUtils.benjaminiHochberg(pvals);
        for (int i=0; i<fdr.length; i++) {
            fdrDonorR1.add(fdr[i]);
        }

        pvals = new double[pvalueAcceptorR1.size()];
        for (int i=0; i<pvals.length; i++) {
            pvals[i] = pvalueAcceptorR1.get(i);
        }
        fdr = StatUtils.benjaminiHochberg(pvals);
        for (int i=0; i<fdr.length; i++) {
            fdrAcceptorR1.add(fdr[i]);
        }

        Set<String> uniqueJunctions = new HashSet<String>();
        for (JunctionKey key: juncDiff.getJunctions().keySet()) {
            JunctionCounts j = juncDiff.getJunctions().get(key);
            if (j.isValidDonor() || j.isValidAcceptor()) {
                uniqueJunctions.add(key.name);
            }
        }

        TabWriter writer = new TabWriter(out);
        writer.write_line("## program: " + NGSUtils.getVersion());
        writer.write_line("## cmd: " + NGSUtils.getArgs());
        writer.write_line("## files: " + StringUtils.join(",", filenames));
        writer.write_line("## groups: " + StringUtils.join(",", groups));

        if (minTotalCount > -1) { 
            writer.write_line("## min-total-count: " + minTotalCount);
        }
        
        if (maxEditDistance > 0) { 
            writer.write_line("## max-edit-distance: " + maxEditDistance);
        }

        for (JunctionDiffSample sample: jdStats.getSamples()) {
            writer.write_line("## sample: " + sample.sampleName + ";" + sample.group + ";" + sample.filename);
        }

        writer.write_line("## total-junctions: "+jdStats.getTotalJunctions());
        writer.write_line("## filtered-junctions: "+jdStats.getFilteredJunctions());
        writer.write_line("## valid-donors: "+jdStats.getValidDonors());
        writer.write_line("## valid-acceptors: "+jdStats.getValidAcceptors());
        writer.write_line("## final-junctions: "+jdStats.getDonorAcceptorFilteredJunctions());
        writer.write_line("## unique-junctions: "+uniqueJunctions.size());
        uniqueJunctions.clear();

        writer.write("junction", "strand");
        writer.write("site_type");
        writer.write("site");

        for (String sample: juncDiff.getSampleNames()) {
            writer.write(sample+"_counts");
        }
        
        for (String sample: juncDiff.getSampleNames()) {
            writer.write(sample+"_site_counts");
        }
        
        for (String sample: juncDiff.getSampleNames()) {
            writer.write(sample+"_site_pct");
        }
        
        writer.write("control-pct", "exp-pct", "pct_diff", "tscore");
        writer.write("juncFDR");
        writer.write("FDR (B-H)");
        writer.eol();

        for (JunctionKey key: juncDiff.getJunctions().keySet()) {
            if (juncDiff.getJunctions().get(key).isValidDonor()) {
                writer.write(key.name, key.strand.toString());
                writer.write("donor", key.donor.name);
                for (int i=0; i<filenames.size(); i++) {
                    writer.write(juncDiff.getJunctions().get(key).getCount(i));
                }
                for (int i=0; i<filenames.size(); i++) {
                    writer.write(juncDiff.getJunctions().get(key).getDonorTotal(i));
                }
                for (int i=0; i<filenames.size(); i++) {
                    writer.write(juncDiff.getJunctions().get(key).getDonorPct(i));
                }
                JunctionStats stats = juncDiff.getJunctions().get(key).calcStats(groups, true);
                writer.write(stats.controlPct);
                writer.write(stats.expPct);
                writer.write(stats.pctDiff);
                writer.write(stats.tScore);
                writer.write(juncDiff.calcPvalue(stats.tScore, true));
                writer.write(fdrDonorR1.remove(0));
                
                writer.eol();
            }
            if (juncDiff.getJunctions().get(key).isValidAcceptor()) {
                writer.write(key.name, key.strand.toString());
                writer.write("acceptor", key.acceptor.name);
                for (int i=0; i<filenames.size(); i++) {
                    writer.write(juncDiff.getJunctions().get(key).getCount(i));
                }
                for (int i=0; i<filenames.size(); i++) {
                    writer.write(juncDiff.getJunctions().get(key).getAcceptorTotal(i));
                }
                for (int i=0; i<filenames.size(); i++) {
                    writer.write(juncDiff.getJunctions().get(key).getAcceptorPct(i));
                }
                JunctionStats stats = juncDiff.getJunctions().get(key).calcStats(groups, false);
                writer.write(stats.controlPct);
                writer.write(stats.expPct);
                writer.write(stats.pctDiff);
                writer.write(stats.tScore);
                writer.write(juncDiff.calcPvalue(stats.tScore, false));
                writer.write(fdrAcceptorR1.remove(0));
                
                writer.eol();
            }
        }

        writer.close();
    }
}

