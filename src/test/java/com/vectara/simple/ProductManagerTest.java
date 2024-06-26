package com.vectara.simple;

import junit.framework.TestCase;

public class ProductManagerTest extends TestCase {

	public void testSyncDirectory() throws InterruptedException {
		
		ProductManager target = new ProductManager();
		target.setDataDirectoryPath("src\\main\\data");
		//target.setInitializeCorpus(false);
		target.initialize();
		
		Thread.sleep(20000);
		
		target.syncDirectory();
		
	}
	
	public void testQuery() throws Exception {
		ProductManager target = new ProductManager();
		target.setDataDirectoryPath("src\\main\\data");
		target.setInitializeCorpus(false);
		target.initialize();

		target.query("Can an MRI SureScan be on is a Pace Polarity and RV Pace Polarity are set to Bipolar", "Medtronic", null);

	}

	public void testQueryOneProduct() throws Exception {
		ProductManager target = new ProductManager();
		target.setDataDirectoryPath("src\\main\\data");
		target.setInitializeCorpus(false);
		target.initialize();

		target.query("Can an MRI SureScan be on is a Pace Polarity and RV Pace Polarity are set to Bipolar", "Medtronic", "Azure S SR MRI SureScan - W3SR01");

	}

	
	public void testQueryNoResults() throws Exception {
		ProductManager target = new ProductManager();
		target.setDataDirectoryPath("src\\main\\data");
		target.setInitializeCorpus(false);
		target.initialize();

		target.query("Can an MRI SureScan be on is a Pace Polarity and RV Pace Polarity are set to Bipolar", "NotFound", null);

	}
	
	
	
}
