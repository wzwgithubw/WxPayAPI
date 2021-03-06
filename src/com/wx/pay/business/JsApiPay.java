package com.wx.pay.business;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.xml.sax.SAXException;

import com.wx.pay.api.WxPayApi;
import com.wx.pay.api.WxPayData;
import com.wx.pay.lib.HttpService;
import com.wx.pay.lib.WxPayConfig;
import com.wx.pay.lib.WxPayException;

public class JsApiPay {

	private static Logger Log = Logger.getLogger(JsApiPay.class);

	// / <summary>
	// / access_token用于获取收货地址js函数入口参数
	// / </summary>
	private static String access_token = null;
	private static long request_access_token_time = 0;

	private static String jsapi_ticket = null;
	private static long request_jsapi_ticket_time = 0;

	/**
	 * 
	 * 网页授权获取用户基本信息的全部过程 详情请参看网页授权获取用户基本信息：http://mp.weixin.qq.com/wiki/17/
	 * c0f37d5704f0b64713d5d2c37b468d75.html 第一步：利用url跳转获取code
	 * 第二步：利用code去获取openid和access_token
	 * 
	 * @throws Exception
	 * 
	 */
	public static void requestOpenId(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		String host = request.getRequestURL().toString();
		host = host.replaceAll(request.getServletPath(), "/OpenIdHandler");
		String redirect_uri = URLEncoder.encode(host, "UTF-8");
		WxPayData data = new WxPayData();
		data.SetValue("appid", WxPayConfig.APPID);
		data.SetValue("redirect_uri", redirect_uri);
		data.SetValue("response_type", "code");
		data.SetValue("scope", "snsapi_base");
		data.SetValue("state", "STATE" + "#wechat_redirect");
		String url = "https://open.weixin.qq.com/connect/oauth2/authorize?"
				+ data.ToUrl();
		Log.debug("Will Redirect to URL : " + url);

		response.sendRedirect(url);
	}

	/**
	 * 
	 * 通过code换取网页授权access_token和openid的返回数据，正确时返回的JSON数据包如下： {
	 * "access_token":"ACCESS_TOKEN", "expires_in":7200,
	 * "refresh_token":"REFRESH_TOKEN", "openid":"OPENID", "scope":"SCOPE",
	 * "unionid": "o6_bmasdasdsad6_2sgVt7hMZOPfL" } 其中access_token可用于获取共享收货地址
	 * openid是微信支付jsapi支付接口统一下单时必须的参数
	 * 更详细的说明请参考网页授权获取用户基本信息：http://mp.weixin.qq.com
	 * /wiki/17/c0f37d5704f0b64713d5d2c37b468d75.html
	 * 
	 * @throws WxPayException
	 * 
	 * @失败时抛异常WxPayException
	 */
	public static String getOpenId(String code) throws Exception {
		Log.debug("Get code : " + code);
		WxPayData data = new WxPayData();
		data.SetValue("appid", WxPayConfig.APPID);
		data.SetValue("secret", WxPayConfig.APPSECRET);
		data.SetValue("code", code);
		data.SetValue("grant_type", "authorization_code");
		String url = "https://api.weixin.qq.com/sns/oauth2/access_token?"
				+ data.ToUrl();

		// 请求url以获取数据
		String result = HttpService.Get(url);

		Log.debug("GetOpenidAndAccessTokenFromCode response : " + result);

		// 保存access_token，用于收货地址获取
		ObjectMapper mapper = new ObjectMapper();
		Map<String, String> jd = mapper.readValue(result, Map.class);

		String access_token = jd.get("access_token");

		// 获取用户openid
		String openid = jd.get("openid");

		Log.debug("Get openid : " + openid);
		Log.debug("Get access_token : " + access_token);

		return openid;
	}

	/**
	 * 调用统一下单，获得下单结果
	 * 
	 * @return 统一下单结果
	 * @throws WxPayException
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws NoSuchAlgorithmException
	 * @失败时抛异常WxPayException
	 */
	public static WxPayData GetUnifiedOrderResult(String openid,
			String total_fee) throws WxPayException, NoSuchAlgorithmException,
			ParserConfigurationException, SAXException, IOException {
		// 统一下单
		WxPayData data = new WxPayData();
		data.SetValue("body", "test");
		data.SetValue("attach", "test");
		data.SetValue("out_trade_no", WxPayApi.GenerateOutTradeNo());
		data.SetValue("total_fee", total_fee);
//		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
//		Calendar c = Calendar.getInstance();
//		data.SetValue("time_start", dateFormat.format(c.getTime()));
//		c.add(Calendar.DATE, 2);
//		data.SetValue("time_expire", dateFormat.format(c.getTime()));

		data.SetValue("goods_tag", "test");
		data.SetValue("trade_type", "JSAPI");
		data.SetValue("openid", openid);

		WxPayData result = WxPayApi.UnifiedOrder(data, 0);
		if (!result.IsSet("appid") || !result.IsSet("prepay_id")
				|| result.GetValue("prepay_id").toString().equalsIgnoreCase("")) {
			Log.error("UnifiedOrder response error!");
			throw new WxPayException("UnifiedOrder response error!");
		}

		return result;
	}

	/**
	 * 
	 * 从统一下单成功返回的数据中获取微信浏览器调起jsapi支付所需的参数， 微信浏览器调起JSAPI时的输入参数格式如下： { "appId" :
	 * "wx2421b1c4370ec43b", //公众号名称，由商户传入 "timeStamp":" 1395712654",
	 * //时间戳，自1970年以来的秒数 "nonceStr" : "e61463f8efa94090b1f366cccfbbb444", //随机串
	 * "package" : "prepay_id=u802345jgfjsdfgsdg888", "signType" : "MD5",
	 * //微信签名方式: "paySign" : "70EA570631E4BB79628FBCA90534C63FF7FADD89" //微信签名 }
	 * 
	 * @return string 微信浏览器调起JSAPI时的输入参数，json格式可以直接做参数用
	 *         更详细的说明请参考网页端调起支付API：http:
	 *         //pay.weixin.qq.com/wiki/doc/api/jsapi.php?chapter=7_7
	 * @throws WxPayException
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 * @throws JsonMappingException
	 * @throws JsonGenerationException
	 * 
	 */
	public static WxPayData GetJsApiParameters(WxPayData unifiedOrderResult)
			throws NoSuchAlgorithmException, WxPayException,
			JsonGenerationException, JsonMappingException, IOException {
		Log.debug("JsApiPay::GetJsApiParam is processing...");

		WxPayData jsApiParam = new WxPayData();
		jsApiParam.SetValue("appId", unifiedOrderResult.GetValue("appid"));
		jsApiParam.SetValue("timeStamp", WxPayApi.GenerateTimeStamp());
		jsApiParam.SetValue("nonceStr", WxPayApi.GenerateNonceStr());
		jsApiParam.SetValue("package",
				"prepay_id=" + unifiedOrderResult.GetValue("prepay_id"));
		jsApiParam.SetValue("signType", "MD5");
		jsApiParam.SetValue("paySign", jsApiParam.MakeSign("MD5"));

		String parameters = jsApiParam.ToJson();

		Log.debug("Get jsApiParam : " + parameters);
		return jsApiParam;
	}

	/**
	 * 
	 * 获取收货地址js函数入口参数,详情请参考收货地址共享接口：http://pay.weixin.qq.com/wiki/doc/api/jsapi.
	 * php?chapter=7_9
	 * 
	 * @return string 共享收货地址js函数需要的参数，json格式可以直接做参数使用
	 * @throws WxPayException
	 */
	public String GetEditAddressParameters(HttpServletRequest request)
			throws WxPayException {
		String parameter = "";
		try {
			String host = request.getRequestURL().toString();
			String queryString = request.getQueryString();
			// 这个地方要注意，参与签名的是网页授权获取用户信息时微信后台回传的完整url
			String url = host
					+ (queryString != null ? ("?" + queryString) : "");

			// 构造需要用SHA1算法加密的数据
			WxPayData signData = new WxPayData();
			signData.SetValue("appid", WxPayConfig.APPID);
			signData.SetValue("url", url);
			signData.SetValue("timestamp", WxPayApi.GenerateTimeStamp());
			signData.SetValue("noncestr", WxPayApi.GenerateNonceStr());
			signData.SetValue("accesstoken", access_token);
			String param = signData.ToUrl();

			Log.debug("SHA1 encrypt param : " + param);
			// SHA1加密
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			String addrSign = new String(md.digest(param.getBytes("UTF-8")));
			Log.debug("SHA1 encrypt result : " + addrSign);

			// 获取收货地址js函数入口参数
			WxPayData afterData = new WxPayData();
			afterData.SetValue("appId", WxPayConfig.APPID);
			afterData.SetValue("scope", "jsapi_address");
			afterData.SetValue("signType", "sha1");
			afterData.SetValue("addrSign", addrSign);
			afterData.SetValue("timeStamp", signData.GetValue("timestamp"));
			afterData.SetValue("nonceStr", signData.GetValue("noncestr"));

			// 转为json格式
			parameter = afterData.ToJson();
			Log.debug("Get EditAddressParam : " + parameter);
		} catch (Exception ex) {
			Log.error(ex.toString());
			throw new WxPayException(ex.toString());
		}

		return parameter;
	}

	public static String getAccessToken() throws Exception {
		long current = System.currentTimeMillis();
		if (access_token == null
				|| current - request_access_token_time >= 7200000) {
			WxPayData data = new WxPayData();
			data.SetValue("appid", WxPayConfig.APPID);
			data.SetValue("secret", WxPayConfig.APPSECRET);
			data.SetValue("grant_type", "client_credential");
			String url = "https://api.weixin.qq.com/cgi-bin/token?"
					+ data.ToUrl();
			String result = HttpService.Get(url);
			ObjectMapper mapper = new ObjectMapper();
			Map<String, String> jd = mapper.readValue(result, Map.class);
			access_token = jd.get("access_token");
			request_access_token_time = System.currentTimeMillis();
		}
		return access_token;
	}

	public static String getJsApiTicket(String access_token) throws Exception {
		long current = System.currentTimeMillis();
		if (jsapi_ticket == null
				|| current - request_jsapi_ticket_time >= 7200000) {
			WxPayData data = new WxPayData();
			data.SetValue("access_token", access_token);
			data.SetValue("type", "jsapi");
			String url = "https://api.weixin.qq.com/cgi-bin/ticket/getticket?"
					+ data.ToUrl();
			String result = HttpService.Get(url);
			ObjectMapper mapper = new ObjectMapper();
			Map<String, String> jd = mapper.readValue(result, Map.class);
			jsapi_ticket = jd.get("ticket");
			request_jsapi_ticket_time = System.currentTimeMillis();
		}

		return jsapi_ticket;
	}

	public static String getSignature(String jsapi_ticket, String nonceStr,
			long timestamp, String url) throws Exception {
		WxPayData data = new WxPayData();
		data.SetValue("jsapi_ticket", jsapi_ticket);
		data.SetValue("noncestr", nonceStr);
		data.SetValue("timestamp", timestamp);
		data.SetValue("url", url);
		return data.MakeSign("SHA1");
	}
}
