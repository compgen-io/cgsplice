package io.compgen.cgsplice;


import io.compgen.cgsplice.cli.BamStats;
import io.compgen.cgsplice.cli.CombineEvents;
import io.compgen.cgsplice.cli.FastaJunctions;
import io.compgen.cgsplice.cli.JunctionCount;
import io.compgen.cgsplice.cli.SpliceDiff;
import io.compgen.cmdline.Help;
import io.compgen.cmdline.License;
import io.compgen.cmdline.MainBuilder;
import io.compgen.common.StringUtils;

import java.io.IOException;

public class CGSplice {
	public static String getVersion() {
		try {
			return MainBuilder.readFile("io/compgen/cgsplice/VERSION");
		} catch (IOException e1) {
			return "unknown";
		}
	}

	private static String args;
	
	public static String getArgs() {
	    return args;
	}

	public static void main(String[] args) throws Exception {
		CGSplice.args = StringUtils.join(" ", args);
		
		new MainBuilder()
		.setProgName("cgsplice")
		.setHelpHeader("cgsplice - Computational Genomics Splicing Tools\n---------------------------------------")
		.setDefaultUsage("Usage: cgsplice cmd [options]")
		.setHelpFooter("http://compgen.io/cgsplice\n"+getVersion())
		.setCategoryOrder(new String[] { "splicing", "help"})
		.addCommand(Help.class)
		.addCommand(License.class)
        .addCommand(JunctionCount.class)
        .addCommand(CombineEvents.class)
        .addCommand(SpliceDiff.class)
        .addCommand(BamStats.class)
        .addCommand(FastaJunctions.class)
		.findAndRun(args);
	}
		
}
