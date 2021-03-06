package com.mooveit.cars.utils;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.zip.Adler32;
import java.util.zip.Checksum;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import com.mooveit.cars.domain.*;
import com.mooveit.cars.repositories.BrandRepositoryI;
import com.mooveit.cars.repositories.EngineRepositoryI;
import com.mooveit.cars.repositories.ModelRepositoryI;
import com.mooveit.cars.repositories.WheelsRepositoryI;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HandlerXMLFord extends DefaultHandler {
		
	private EngineRepositoryI engineRepository;
  private WheelsRepositoryI wheelsRepository;
  private ModelRepositoryI modelRepository;  
  
	private Model model;
	private Model parentModel = null;
	private Engine engine;
	private Wheels wheels;
	private Brand brand;
	
	private static final String FORD_NAME = "ford";
	
	private Deque<Model> lstModelsQueue = new LinkedList<>();

	private Checksum checksum = new Adler32();

	public HandlerXMLFord(BrandRepositoryI brandRepository, 
												EngineRepositoryI engineRepository,
												WheelsRepositoryI wheelsRepository,
												ModelRepositoryI modelRepository) {
		
		this.engineRepository = engineRepository;
		this.wheelsRepository = wheelsRepository;
		this.modelRepository = modelRepository;
		
		brand = brandRepository.getByName(FORD_NAME);			
		if (brand == null) {
			brand = brandRepository.save(new Brand(FORD_NAME));
		}
		
	}
	
	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				
		if (qName.equalsIgnoreCase("MODEL")) {			
			
			model = modelRepository.getByName(attributes.getValue("name"));
			if (model == null) {
				log.debug("new model({})", attributes.getValue("name"));
				model = new Model();
				model.setName(attributes.getValue("name"));
			}
			
			model.setBrand(brand);
			model.setFromYear(attributes.getValue("from"));			
			model.setToYear(attributes.getValue("to")); 
			model.setType(attributes.getValue("type"));			
			model.setLine(attributes.getValue("line"));			
						
		} else if (qName.equalsIgnoreCase("ENGINE")) {
			
			engine = engineRepository.getByPowerAndType(attributes.getValue("power"), attributes.getValue("type"));
			if (engine == null) {
				log.debug("new Engine({},{})", attributes.getValue("power"), attributes.getValue("type"));
				engine = new Engine(attributes.getValue("power"), attributes.getValue("type"));				
			}

		} else if (qName.equalsIgnoreCase("WHEELS")) {
			
			wheels = wheelsRepository.getBySizeAndType(attributes.getValue("size"), attributes.getValue("type"));
			if (wheels == null) {
				log.debug("new Wheels({},{})", attributes.getValue("size"), attributes.getValue("type"));
				wheels = new Wheels(attributes.getValue("size"), attributes.getValue("type"));
			}
			 
		} else if (qName.equalsIgnoreCase("SUBMODELS")) {
			
			lstModelsQueue.push(model);			

		}
		
	}
	
	@Override
  public void endElement(String uri, String localName, String qName) throws SAXException {		
		
		if (qName.equalsIgnoreCase("MODEL")) {
			
			/*
			 * Load photo file
			 */
			
			Resource resource = new ClassPathResource(model.getName() + ".jpeg");
			
			try {

				File file = resource.getFile();
				byte[] fileContent = FileUtils.readFileToByteArray(file);

				checksum.update(fileContent, 0, fileContent.length);
				long photoChecksum = checksum.getValue();

				if (photoChecksum != model.getPhotoChecksum()) {

					model.setPhoto(fileContent);
					model.setPhotoChecksum(photoChecksum);

				}

			} catch (IOException e) {

				log.debug("photo do not exists:{}", model.getName() + ".jpg");
				model.setPhoto(null);
				model.setPhotoChecksum(0);

			}

			log.debug("endElement.Model({})", model.getName());
			model = modelRepository.save(model);
			parentModel = lstModelsQueue.peekLast();

			// assign child to the parent
			if (parentModel != null && !parentModel.getSubModels().contains(model)) {
				parentModel.getSubModels().add(model);								
			}
			
			// save the children
			for (Model modelItem : model.getSubModels()) {
				modelItem.setParentModel(model);
				modelRepository.save(modelItem);
			}
			
		} else if (qName.equalsIgnoreCase("ENGINE")) {
			
			log.debug("endElement.Engine({},{})", engine.getPower(), engine.getType());
			engine = engineRepository.save(engine);
			model.setEngine(engine);
		
		} else if (qName.equalsIgnoreCase("WHEELS")) {
			
			log.debug("endElement.Wheels({},{})", wheels.getSize(), wheels.getType());
			wheels = wheelsRepository.save(wheels);
			model.setWheels(wheels);
			
		} else if (qName.equalsIgnoreCase("SUBMODELS")) {
			
			log.debug("endElement.submodels()");
			model = lstModelsQueue.removeLast();
			 
		}

 	}

}
