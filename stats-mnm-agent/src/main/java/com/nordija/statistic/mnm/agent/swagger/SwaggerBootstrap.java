package com.nordija.statistic.mnm.agent.swagger;

import javax.servlet.http.HttpServlet;

import com.wordnik.swagger.jaxrs.JaxrsApiReader;

public class SwaggerBootstrap extends HttpServlet {
  static {
    JaxrsApiReader.setFormatString("");
  }
}