/*
 * @Author
 * @Copyright
 * @Date
 */
package com.ncpl.sales;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

// This release starts the Finance workspace and its accounts only.
// ERP operational controllers, jobs and mail services are excluded from component scanning.
@SpringBootApplication(scanBasePackages = {"com.ncpl.sales.cashflow", "com.ncpl.sales.security"})
@EnableScheduling
public class SalesApp extends SpringBootServletInitializer{

	public static void main(String[] args) {
	//	System.setProperty("spring.devtools.restart.enabled", "false"); //On change of any file avoid restart
		SpringApplication.run(SalesApp.class, args);
	}
	
	/**
	 * For running as a web application
	 */

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		 return builder.sources(SalesApp.class);
	}
}

