package org.haboob;

import org.jcyclone.core.boot.JCyclone;

import java.net.URL;


public class Main {

	public static void main(String[] args) throws Exception {
		URL url = Main.class.getResource("haboob.cfg");
		new JCyclone(url.getFile());
	}
}
