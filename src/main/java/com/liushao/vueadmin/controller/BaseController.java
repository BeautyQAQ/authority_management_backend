package com.liushao.vueadmin.controller;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;

public class BaseController {
    @Autowired
    HttpServletRequest req;
 }
