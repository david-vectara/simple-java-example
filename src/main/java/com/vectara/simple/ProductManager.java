package com.vectara.simple;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.vectara.ApiClient;
import com.vectara.JSON;
import com.vectara.api.CorporaApi;
import com.vectara.api.QueriesApi;
import com.vectara.api.UploadApi;
import com.vectara.client.YamlAppConfig;
import com.vectara.client.auth.AuthUtil;
import com.vectara.client.config.VectaraConfig;
import com.vectara.model.Corpus;
import com.vectara.model.CreateCorpusRequest;
import com.vectara.model.FilterAttribute;
import com.vectara.model.FilterAttribute.LevelEnum;
import com.vectara.model.FilterAttribute.TypeEnum;
import com.vectara.model.GenerationParameters;
import com.vectara.model.IndividualSearchResult;
import com.vectara.model.KeyedSearchCorpus;
import com.vectara.model.Language;
import com.vectara.model.ListCorporaResponse;
import com.vectara.model.QueryFullResponse;
import com.vectara.model.QueryRequest;
import com.vectara.model.SearchCorporaParameters;

public class ProductManager {

	private ApiClient apiClient;

	private static final String CORPUS_NAME = "WL - Product Information";

	private static final String CORPUS_KEY = "product_info_";

	private String corpusKey;

	private String dataDirectoryPath;

	private boolean initializeCorpus = true;

	/**
	 * This will create our instance of a product manager, ensuring that our corpus
	 * is setup and creating if not already present.
	 * 
	 * We will also validate that the data directory is present.
	 * 
	 * TODO Check that corpus doesn't already exist TODO Move most of the
	 * initialization into client project TODO Add "builder" for filter attributes
	 * with sensible defaults. TODO Add logging framework
	 */
	public void initialize() {

		System.out.println("Initializing our Authentication using YAML file in user home directory");

		// You can wire authentication into the client however you please, this is just
		// a convenient way to do it
		// with code examples that avoids putting credentials into your projects.
		YamlAppConfig yamlConfig = new YamlAppConfig();
		VectaraConfig config = yamlConfig.getVectaraConfig();

		// Manually Wire Spring beans for now.
		AuthUtil authUtil = new AuthUtil();
		authUtil.setConfig(config);
		authUtil.initialize();

		this.apiClient = authUtil.createApiClient();
		this.apiClient.setConnectTimeout(60000);
		this.apiClient.setReadTimeout(60000);
		this.apiClient.setWriteTimeout(60000);

		if (initializeCorpus) {
			delete();
			initializeCorpus();
		} else {
			findCorpusByName();
		}
	}

	private void findCorpusByName() {
		try {
			CorporaApi corporaApi = new CorporaApi(this.apiClient);
			ListCorporaResponse response = corporaApi.listCorpora(10, CORPUS_NAME, null);
			
			List<Corpus> corpora = response.getCorpora();
			List<Corpus> nameFilteredCorpora = new ArrayList<Corpus>();
			for (Corpus potential : corpora) {
				if (potential.getName().equals(CORPUS_NAME)) {
					nameFilteredCorpora.add(potential);
				}
			}
			
			if (nameFilteredCorpora.size() == 0) {
				throw new Exception("We have skipped initialization but have no existing corpora");
			} else if (nameFilteredCorpora.size() > 1) {
				throw new Exception("We were expecting a single corpus but have found [" + nameFilteredCorpora.size() + "]");
			} else {
				this.corpusKey = nameFilteredCorpora.get(0).getKey();
			}
			
		} catch (Exception e) {
			throw new RuntimeException("Unable to lookup corpus key: " + e.getMessage(), e);
		}
	}

	private void initializeCorpus() {
		CorporaApi corporaApi = new CorporaApi(this.apiClient);

		CreateCorpusRequest request = new CreateCorpusRequest();
		request.setName(CORPUS_NAME);
		request.setKey(CORPUS_KEY + System.currentTimeMillis());
		request.setDescription("An example Vectara Corpus Storing documents about products and their category.");

		// Add our Product attribute.
		FilterAttribute productAttr = new FilterAttribute();
		productAttr.setName("Product");
		productAttr.setDescription("This is the product name, unique within the manufacturer");
		productAttr.setLevel(LevelEnum.DOCUMENT);
		productAttr.setType(TypeEnum.TEXT);
		productAttr.setIndexed(true);
		request.addFilterAttributesItem(productAttr);

		// Add our Category attribute.
		FilterAttribute manufacturerAttr = new FilterAttribute();
		manufacturerAttr.setName("Manufacturer");
		manufacturerAttr.setDescription("This is the product manufacturer");
		manufacturerAttr.setLevel(LevelEnum.DOCUMENT);
		manufacturerAttr.setType(TypeEnum.TEXT);
		manufacturerAttr.setIndexed(true);
		request.addFilterAttributesItem(manufacturerAttr);

		try {
			Corpus result = corporaApi.createCorpus(request);
			System.out.println("New corpus created with ID " + result.getId());
			this.corpusKey = result.getKey();
		} catch (Exception e) {
			throw new RuntimeException("Unable to create new corpus: " + e.getMessage(), e);
		}
	}

	/**
	 * This will crawl our data directory for information files and index any which
	 * need to be ingested for our example.
	 */
	public void syncDirectory() {
		System.out.println("Performing bulk indexing of data directory");

		System.out.println("Checking configuraiton values");

		if (StringUtils.isBlank(dataDirectoryPath)) {
			System.out.println("The data directory is blank, this must be set for the product manager to ingest data");
			return;
		}

		File dataDirectory = new File(dataDirectoryPath);
		System.out.println("Found data directory: " + dataDirectory.getAbsolutePath());

		if (dataDirectory.exists() && dataDirectory.isDirectory() && dataDirectory.canRead()) {
			System.out.println("The data directory has been validated");
		}
		List<String> directories = findDirectoryNames(dataDirectory);

		for (String manufacturer : directories) {
			syncManufacturer(manufacturer);
		}

	}

	private void syncManufacturer(String manufacturer) {
		File manufacturerDirectory = new File(this.dataDirectoryPath + File.separator + manufacturer);
		List<String> directories = findDirectoryNames(manufacturerDirectory);

		for (String productName : directories) {
			syncProducts(manufacturer, productName);
		}
	}

	private void syncProducts(String manufacturer, String productName) {
		File manufacturerDirectory = new File(
				this.dataDirectoryPath + File.separator + manufacturer + File.separator + productName);
		List<String> fileNamesToUpload = findUploadFiles(manufacturerDirectory, new String[] { "pdf", "doc", "docx" });

		UploadApi uploadApi = new UploadApi(this.apiClient);

		try {

			for (String fileNameToUpload : fileNamesToUpload) {
				System.out.println("For manufacturer [" + manufacturer + "] we are uploading product [" + productName
						+ "] with file [" + fileNameToUpload + "]");

				File fileToUpload = new File(manufacturerDirectory, fileNameToUpload);

				Map<String, Object> metadata = new HashMap<String, Object>();
				metadata.put("Manufacturer", manufacturer);
				metadata.put("Product", productName);

				uploadApi.uploadFile(this.corpusKey, fileToUpload, metadata);

			}

		} catch (Exception e) {
			throw new RuntimeException("Unable to upload file: " + e.getMessage(), e);
		}
	}

	/**
	 * This will run a query against the given category and product_name. If these
	 * are left blank, we will run our query against all products/categories.
	 * 
	 * @param query
	 * @param manufacturer
	 * @param productName
	 */
	public void query(String query, String manufacturer, String productName) {

		try {
		QueriesApi queryService = new QueriesApi(this.apiClient);
		
		// Create the query request
		QueryRequest request = new QueryRequest();
		request.setQuery(query);
		
		// Set the search corpora params
		SearchCorporaParameters searchCorporaParams = new SearchCorporaParameters();
		KeyedSearchCorpus keyedSearchCorpus = new KeyedSearchCorpus();
		keyedSearchCorpus.setCorpusKey(corpusKey);
		
		// TODO convert Map of query attributes to filter string
		Map<String,String> filterMap = new HashMap<String, String>();
		if (StringUtils.isNotBlank(manufacturer)) {
			filterMap.put("Manufacturer", manufacturer);
		}
		if (StringUtils.isNotBlank(productName)) {
			filterMap.put("Product", productName);
		}
		if (filterMap.size() > 0) {
			List<String> keys = new ArrayList<String>(filterMap.keySet());
			Collections.sort(keys);
			
			List<String> expressions = new ArrayList<String>(); 
			for (String key : keys) {
				String value = filterMap.get(key);
				expressions.add("doc." + key + " = '" + value + "'");
			}
			
			String finalFilter;
			if (expressions.size() == 1) {
				finalFilter = expressions.get(0);
			} else {
				finalFilter = "((" + StringUtils.join(expressions, ") and (") + "))";
			}
			System.out.println("Metadata filter is ["+finalFilter+"]");
			keyedSearchCorpus.setMetadataFilter(finalFilter);
		}
		
		
		searchCorporaParams.addCorporaItem(keyedSearchCorpus);
		request.setSearch(searchCorporaParams);
		
		GenerationParameters generationParams = new GenerationParameters();
		generationParams.setMaxUsedSearchResults(10);
		generationParams.setPromptName("vectara-summary-ext-v1.3.0");
		generationParams.setResponseLanguage(Language.ENG);
		
		request.setGeneration(generationParams);
		
		
		QueryFullResponse response = queryService.query(request);
		List<IndividualSearchResult> results = response.getSearchResults();
		System.out.println("We had ["+results.size()+"] results");
		
		int index = 1;
		for (IndividualSearchResult result : results) {
			System.out.println("Result " + index + ": " + result.getDocumentId());
			
			Map<String,Object> filteredMetadata = new HashMap<String, Object>();
			for (String key : new String[]{"Manufacturer", "Product"}) {
				if (result.getDocumentMetadata().containsKey(key)) {
					filteredMetadata.put(key, result.getDocumentMetadata().get(key));
				}
			}
			
			System.out.println("\tMetadata: " + JSON.serialize(filteredMetadata));
			System.out.println("\tText: " + result.getText());
			System.out.println("\tScore: " + result.getScore());
			System.out.println("\n\n\n===========================================================");
			index++;
		}
		
		System.out.println("Summary\n==================================================\n" + response.getSummary());
		} catch (Exception e) {
			throw new RuntimeException("Unable to run query: " + e.getMessage(), e);
		}
		
	}

	public void delete() {
		System.out.println("Deleting existing corpus with name [" + CORPUS_NAME + "] if it exists");
		try {

			CorporaApi corporaApi = new CorporaApi(this.apiClient);

			// TODO Move this into client
			// TODO Add iteration with page key.
			ListCorporaResponse response = corporaApi.listCorpora(10, CORPUS_NAME, null);
			for (Corpus existing : response.getCorpora()) {
				System.out.println("Existing corpus [" + existing.getKey() + "] found, deleting");
				corporaApi.deleteCorpus(existing.getKey());
				Thread.sleep(10000);
				System.out.println("Deletion complete");
			}

		} catch (Exception e) {
			throw new RuntimeException("Unable to delete corpus: " + e.getMessage(), e);
		}
	}

	private List<String> findUploadFiles(File dataDirectory, String[] extensions) {
		String[] directoriesTemp = dataDirectory.list(new FilenameFilter() {
			@Override
			public boolean accept(File current, String name) {
				for (String extension : extensions) {
					if (name.endsWith("." + extension)) {
						return true;
					}
				}
				return false;
			}
		});
		List<String> directories = Arrays.asList(directoriesTemp);
		System.out.println("Found: " + directories);
		return directories;
	}

	private List<String> findDirectoryNames(File dataDirectory) {
		String[] directoriesTemp = dataDirectory.list(new FilenameFilter() {
			@Override
			public boolean accept(File current, String name) {
				return new File(current, name).isDirectory();
			}
		});
		List<String> directories = Arrays.asList(directoriesTemp);
		System.out.println("Found: " + directories);
		return directories;
	}

	public void setDataDirectoryPath(String dataDirectoryPath) {
		this.dataDirectoryPath = dataDirectoryPath;
	}

	public void setInitializeCorpus(boolean initializeCorpus) {
		this.initializeCorpus = initializeCorpus;
	}

}
