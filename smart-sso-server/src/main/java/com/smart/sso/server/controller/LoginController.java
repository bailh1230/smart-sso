package com.smart.sso.server.controller;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.smart.sso.server.model.SsoApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.smart.sso.client.constant.Oauth2Constant;
import com.smart.sso.client.constant.SsoConstant;
import com.smart.sso.client.rpc.Result;
import com.smart.sso.client.rpc.SsoUser;
import com.smart.sso.server.constant.AppConstant;
import com.smart.sso.server.enums.ClientTypeEnum;
import com.smart.sso.server.service.AppService;
import com.smart.sso.server.service.UserService;
import com.smart.sso.server.session.CodeManager;
import com.smart.sso.server.session.TicketGrantingTicketManager;
import com.smart.sso.server.util.CookieUtils;

/**
 * 单点登录管理
 * 
 * @author Joe
 */
@Controller
@RequestMapping("/login")
public class LoginController{

	@Autowired
	private CodeManager codeManager;
	@Autowired
	private TicketGrantingTicketManager ticketGrantingTicketManager;
	@Autowired
	private UserService userService;
	@Autowired
	private AppService appService;

	/**
	 * 登录页
	 * 
	 * @param redirectUri
	 * @param clientId
	 * @param request
	 * @return
	 */
	@RequestMapping(method = RequestMethod.GET)
	public String login(
			@RequestParam(value = SsoConstant.REDIRECT_URI, required = true) String redirectUri,
			@RequestParam(value = Oauth2Constant.CLIENT_ID, required = true) String clientId,
			HttpServletRequest request) throws UnsupportedEncodingException {
		String tgt = CookieUtils.getCookie(request, AppConstant.TGC);
		if (StringUtils.isEmpty(tgt) || ticketGrantingTicketManager.get(tgt) == null) {
			return goLoginPath(redirectUri, clientId, request);
		}
		return generateCodeAndRedirect(redirectUri, tgt);
	}
	
	/**
	 * 登录提交
	 * 
	 * @param redirectUri
	 * @param clientId
	 * @param username
	 * @param password
	 * @param request
	 * @param response
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	@RequestMapping(method = RequestMethod.POST)
	public String login(
			@RequestParam(value = SsoConstant.REDIRECT_URI, required = true) String redirectUri,
			@RequestParam(value = Oauth2Constant.CLIENT_ID, required = true) String clientId,
			@RequestParam String username,
			@RequestParam String password,
			HttpServletRequest request, HttpServletResponse response, Model model) throws UnsupportedEncodingException {

		if(!appService.exists(clientId)) {
			request.setAttribute("errorMessage", "非法应用");
			return goLoginPath(redirectUri, clientId, request);
		}
		
		Result<SsoUser> result = userService.login(username, password);
		if (!result.isSuccess()) {
			request.setAttribute("errorMessage", result.getMessage());
			return goLoginPath(redirectUri, clientId, request);
		}

		String tgt = CookieUtils.getCookie(request, AppConstant.TGC);
		if (StringUtils.isEmpty(tgt) || ticketGrantingTicketManager.refresh(tgt) == null) {
			tgt = ticketGrantingTicketManager.generate(result.getData());

			// TGT存cookie，和Cas登录保存cookie中名称一致为：TGC
			CookieUtils.addCookie(AppConstant.TGC, tgt, "/", request, response);
		}
		// return generateCodeAndRedirect(redirectUri, tgt);

		//尝试生成授权页面
		// String loginUrl = new StringBuilder().append(Oauth2Constant.AUTH_URL).append("?")
		// 		.append(Oauth2Constant.CLIENT_ID).append("=").append(clientId).append("&")
		// 		.append(SsoConstant.REDIRECT_URI).append("=")
		// 		.append(URLEncoder.encode(redirectUri, "utf-8")).toString();
		// return "redirect:" + loginUrl;
		SsoApp ssoApp = appService.getApp(clientId);
		model.addAttribute(Oauth2Constant.CLIENT_NAME, ssoApp.getClientName());
		model.addAttribute(Oauth2Constant.CLIENT_ID, clientId);
		// 当前服务端口号
		model.addAttribute(SsoConstant.REDIRECT_URI, redirectUri);
		return Oauth2Constant.AUTH;
	}

	/**
	 * 设置request的redirectUri和clientId参数，跳转到登录页
	 * 
	 * @param redirectUri
	 * @param request
	 * @return
	 */
	private String goLoginPath(String redirectUri, String clientId, HttpServletRequest request) {
		request.setAttribute(SsoConstant.REDIRECT_URI, redirectUri);
		request.setAttribute(Oauth2Constant.CLIENT_ID, clientId);
		return AppConstant.LOGIN_PATH;
	}
	
	/**
	 * 生成授权码，跳转到redirectUri
	 * 
	 * @param redirectUri
	 * @param tgt
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	private String generateCodeAndRedirect(String redirectUri, String tgt) throws UnsupportedEncodingException {
		// 生成授权码
		String code = codeManager.generate(tgt, ClientTypeEnum.WEB, redirectUri);
		return "redirect:" + authRedirectUri(redirectUri, code);
	}

	/**
	 * 将授权码拼接到回调redirectUri中
	 * 
	 * @param redirectUri
	 * @param code
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	private String authRedirectUri(String redirectUri, String code) throws UnsupportedEncodingException {
		StringBuilder sbf = new StringBuilder(redirectUri);
		if (redirectUri.indexOf("?") > -1) {
			sbf.append("&");
		}
		else {
			sbf.append("?");
		}
		sbf.append(Oauth2Constant.AUTH_CODE).append("=").append(code);
		return URLDecoder.decode(sbf.toString(), "utf-8");
	}

}