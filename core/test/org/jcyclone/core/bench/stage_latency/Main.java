package org.jcyclone.bench.stage_latency;

import org.jcyclone.core.boot.JCyclone;

import java.net.URL;

public class Main {

	public static void main(String[] args) throws Exception {
		URL url = Main.class.getClassLoader().getResource("bench-jcyclone.cfg");
		new JCyclone(url.getFile());
	}

}
