package io.compgen.cgsplice.cli;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;
import io.compgen.common.TallyCounts;
import io.compgen.common.progress.FileChannelStats;
import io.compgen.common.progress.ProgressMessage;
import io.compgen.common.progress.ProgressUtils;
import io.compgen.ngsutils.annotation.GenomeSpan;
import io.compgen.ngsutils.bam.Orientation;
import io.compgen.ngsutils.bam.support.ReadUtils;
import io.compgen.ngsutils.support.CloseableFinalizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Command(name="bam-stats", desc="Stats about a BAM file and junction coverage", category="splicing")
public class BamStats extends AbstractOutputCommand {
    private String filename = null;
    private boolean lenient = false;
    private boolean silent = false;
    private Orientation orient = Orientation.UNSTRANDED;

    @UnnamedArg(name = "FILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc="Use lenient validation strategy", name="lenient")
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Option(desc="Use silent validation strategy", name="silent")
    public void setSilent(boolean silent) {
        this.silent = silent;
    }    
    @Option(desc="Library is in FR orientation", name="library-fr")
    public void setLibraryFR(boolean val) {
        if (val) {
            orient = Orientation.FR;
        }
    }

    @Option(desc="Library is in RF orientation", name="library-rf")
    public void setLibraryRF(boolean val) {
        if (val) {
            orient = Orientation.RF;
        }
    }

    @Option(desc="Library is in unstranded orientation (default)", name="library-unstranded")
    public void setLibraryUnstranded(boolean val) {
        if (val) {
            orient = Orientation.UNSTRANDED;
        }
    }

    @Exec
    public void exec() throws IOException, CommandArgumentException {
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input BAM filename!");
        }
        
        SamReaderFactory readerFactory = SamReaderFactory.makeDefault();
        if (lenient) {
            readerFactory.validationStringency(ValidationStringency.LENIENT);
        } else if (silent) {
            readerFactory.validationStringency(ValidationStringency.SILENT);
        }

        SamReader reader = null;
        String name;
        FileChannel channel = null;
        if (filename.equals("-")) {
            reader = readerFactory.open(SamInputResource.of(System.in));
            name = "<stdin>";
        } else {
            File f = new File(filename);
            FileInputStream fis = new FileInputStream(f);
            channel = fis.getChannel();
            reader = readerFactory.open(SamInputResource.of(fis));
            name = f.getName();
        }

        final Set<String> junctions = new HashSet<String>();
        final Map<String, Set<String>> readJuncBuf = new HashMap<String, Set<String>>();
        final Map<String, Integer> readEndPos = new HashMap<String, Integer>();
        final TallyCounts juncCounts = new TallyCounts();
        
        long total = 0;
        long mapped = 0;
        long unmapped = 0;
        long junctionSpanning = 0;        
        long overlapping = 0;        

        Iterator<SAMRecord> it = ProgressUtils.getIterator(name, reader.iterator(), (channel == null)? null : new FileChannelStats(channel), 
                new ProgressMessage<SAMRecord>() {
                    long i = 0;
                    @Override
                    public String msg(SAMRecord current) {
                        i++;
                        return i+" "+current.getReadName() + " " + readJuncBuf.size()+"/"+junctions.size();
                    }
                }, new CloseableFinalizer<SAMRecord>(){});


        while (it.hasNext()) {
            SAMRecord read = it.next();

            // summary counts only based on first reads
            if (!read.getReadPairedFlag() || read.getFirstOfPairFlag()) {
                total++;
            }

            if (!read.getReadPairedFlag() || read.getFirstOfPairFlag()) {
            	if ((read.getReadPairedFlag() && read.getMateUnmappedFlag()) || read.getReadUnmappedFlag()) {
            		unmapped++;
            	} else {
            		mapped++;
            	}
            }
            
            if (read.getReadPairedFlag() && !read.getProperPairFlag()) {
                // We only profile proper pairs...
                continue;
            }

            boolean alreadyInBuf = readJuncBuf.containsKey(read.getReadName());
        	if (!alreadyInBuf) {
        		readJuncBuf.put(read.getReadName(), new HashSet<String>());
        		
        		if (read.getReadPairedFlag()) { 
        			readEndPos.put(read.getReadName(), read.getAlignmentEnd());
        		}
        	}
            
            for (GenomeSpan span: ReadUtils.getJunctionsForRead(read, orient)) {
            	String junc = span.toString();
            	junctions.add(junc);
        		readJuncBuf.get(read.getReadName()).add(junc);
            }

            if (alreadyInBuf || !read.getReadPairedFlag()) { 
            	// must be on the second of the pair, regardless of first/second flags
            	int juncCount = readJuncBuf.get(read.getReadName()).size();
            	if (juncCount > 0) {
            		junctionSpanning++;
            	}
            	
            	juncCounts.incr(juncCount);
            	
            	// Do the first/second pairs overlap?
        		if (read.getReadPairedFlag()) { 
        			int readStart = read.getAlignmentStart()-1;
        			int firstEnd = readEndPos.get(read.getReadName());

        			if (readStart < firstEnd) {
	            		overlapping++;
	            	}

        			readEndPos.remove(read.getReadName());
        		}
        		
            	readJuncBuf.remove(read.getReadName());
            }
        }
        
        reader.close();

        println("Total-reads:\t" + total);
        println("Mapped-reads:\t" + mapped);
        println("Unmapped/unpaired-reads:\t" + unmapped);
        println("");
        println("Junction-spanning-reads:\t" + junctionSpanning);
        println("Non-junction-spanning-reads:\t" + juncCounts.getCount(0));
        println("");
        println("Unique-junctions:\t" + junctions.size());
        println("");
        println("Overlapping-reads:\t" + overlapping);
        println("");
        println("[Junctions per fragment]");
        for (int i=0; i<=juncCounts.getMax(); i++) {
        	println(i+"\t"+juncCounts.getCount(i));
        }
    }
    
    private void println(String s) throws IOException {
        out.write((s+"\n").getBytes());
    }
}
