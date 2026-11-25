package com.pivot.banking.credit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the credit microservice. */
@SpringBootApplication
public class CreditServiceApplication {

  /** Boots the Spring application context. */
  public static void main(String[] args) {
    SpringApplication.run(CreditServiceApplication.class, args);
  }
}
