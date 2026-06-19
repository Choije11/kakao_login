package com.example.kakao_login;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpSession;

@Controller
public class KakaoController {

    @Value("${kakao.client.id}")
    private String clientId;

    // 1. 메인 페이지 (로그인 여부 체크)
    @GetMapping("/")
    public String index(HttpSession session, Model model) {
        String nickname = (String) session.getAttribute("loginUser");
        if (nickname != null) {
            model.addAttribute("nickname", nickname);
            model.addAttribute("isLoggedIn", true);
        } else {
            model.addAttribute("isLoggedIn", false);
        }
        return "index";
    }

    // 2. 회원가입 페이지 이동
    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    // 3. 카카오 로그인 시작 주소 생성
    @GetMapping("/auth/kakao")
    public String kakaoLogin() {
        String kakaoAuthUrl = "https://kauth.kakao.com/oauth/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=http://localhost:8080/auth/kakao/callback"
                + "&response_type=code";
        return "redirect:" + kakaoAuthUrl;
    }

    // 4. 카카오 로그인 콜백 (인증 처리 후 리다이렉트)
    @GetMapping("/auth/kakao/callback")
    public String kakaoCallback(@RequestParam("code") String code, HttpSession session) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ObjectMapper objectMapper = new ObjectMapper();

            // 토큰 요청
            String tokenUrl = "https://kauth.kakao.com/oauth/token";
            HttpHeaders tokenHeaders = new HttpHeaders();
            tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> tokenParams = new LinkedMultiValueMap<>();
            tokenParams.add("grant_type", "authorization_code");
            tokenParams.add("client_id", clientId);

            // 🔥 핵심 수정: 프로퍼티 변수 대신 텍스트로 주소를 완전히 고정하여 주소 불일치 에러를 원천 차단합니다.
            tokenParams.add("redirect_uri", "http://localhost:8080/auth/kakao/callback");
            tokenParams.add("code", code);

            HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(tokenParams, tokenHeaders);
            ResponseEntity<String> tokenResponse = restTemplate.postForEntity(tokenUrl, tokenRequest, String.class);

            JsonNode tokenJson = objectMapper.readTree(tokenResponse.getBody());
            String accessToken = tokenJson.get("access_token").asText();

            // 사용자 정보 요청
            String userUrl = "https://kapi.kakao.com/v2/user/me";
            HttpHeaders userHeaders = new HttpHeaders();
            userHeaders.add("Authorization", "Bearer " + accessToken);
            userHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> userRequest = new HttpEntity<>(userHeaders);
            ResponseEntity<String> userResponse = restTemplate.postForEntity(userUrl, userRequest, String.class);

            JsonNode userJson = objectMapper.readTree(userResponse.getBody());
            String nickname = userJson.get("properties").get("nickname").asText();

            // 세션에 로그인한 사용자의 닉네임을 저장
            session.setAttribute("loginUser", nickname);

            // 주소창을 깔끔하게 정리하기 위해 /welcome 으로 이동시킵니다.
            return "redirect:/welcome";

        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/?error=failed";
        }
    }

    // 5. 로그인 성공 전용 주소 처리
    @GetMapping("/welcome")
    public String welcome(HttpSession session, Model model) {
        String nickname = (String) session.getAttribute("loginUser");

        // 만약 로그인 세션이 없는데 주소로 강제 접근하면 홈으로 쫓아냅니다.
        if (nickname == null) {
            return "redirect:/";
        }

        model.addAttribute("nickname", nickname);
        return "welcome"; // templates/welcome.html 파일이 열립니다.
    }

    // 6. 로그아웃 기능
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate(); // 세션 완전 무효화
        return "redirect:/";
    }
}