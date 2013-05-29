package com.rhinoforms.formparser;

import java.io.IOException;
import java.util.List;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import com.rhinoforms.TestApplicationContext;
import com.rhinoforms.resourceloader.ResourceLoaderException;

public class SelectOptionHelperTest {

	private SelectOptionHelper selectOptionHelper;

	@Before
	public void setup() throws ResourceLoaderException, IOException {
		TestApplicationContext applicationContext = new TestApplicationContext();
		selectOptionHelper = new SelectOptionHelper(applicationContext.getResourceLoader());
	}
	
	@Test
	public void testSingleValues() throws ResourceLoaderException {
		List<SelectOptionPojo> options = selectOptionHelper.loadOptions("com/rhinoforms/formparser/csv-single-values.csv");
		Assert.assertEquals(3, options.size());
		Assert.assertEquals("one", options.get(0).getText());
		Assert.assertEquals(null, options.get(0).getValue());
	}
	
	@Test
	public void testValuePairs() throws ResourceLoaderException {
		List<SelectOptionPojo> options = selectOptionHelper.loadOptions("com/rhinoforms/formparser/csv-value-pairs.csv");
		Assert.assertEquals(3, options.size());
		Assert.assertEquals("one", options.get(0).getText());
		Assert.assertEquals("1", options.get(0).getValue());
	}

	@Test
	public void testValuePairsEscaped() throws ResourceLoaderException {
		List<SelectOptionPojo> options = selectOptionHelper.loadOptions("com/rhinoforms/formparser/csv-value-pairs-escaped.csv");
		Assert.assertEquals(3, options.size());
		Assert.assertEquals("one,with,commas", options.get(0).getText());
	}
	
}
