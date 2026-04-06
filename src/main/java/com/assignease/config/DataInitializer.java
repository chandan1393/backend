package com.assignease.config;

import com.assignease.entity.SiteConfig;
import com.assignease.entity.Testimonial;
import com.assignease.entity.User;
import com.assignease.repository.SiteConfigRepository;
import com.assignease.repository.TestimonialRepository;
import com.assignease.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SiteConfigRepository configRepo;
    private final TestimonialRepository testimonialRepo;

    @Override
    public void run(String... args) {
        seedAdmin();
        seedSiteConfig();
        seedTestimonials();
    }

    private void seedAdmin() {
        if (!userRepository.existsByEmail("admin@assignease.com")) {
            User admin = User.builder()
                .fullName("Super Admin").email("admin@assignease.com")
                .password(passwordEncoder.encode("Admin@123"))
                .role(User.Role.ROLE_ADMIN).enabled(true).firstLogin(false)
                .build();
            userRepository.save(admin);
            log.info("✅ Default admin: admin@assignease.com / Admin@123");
        }
    }

    private void seedSiteConfig() {
        Map<String, String> defaults = Map.of(
            "company_name",    "AssignEase",
            "company_tagline", "Academic Excellence Delivered to You",
            "company_email",   "support@assignease.com",
            "company_phone",   "+91 98765 43210",
            "company_address", "New Delhi, India",
            "whatsapp_number", "+91 98765 43210"
        );
        defaults.forEach((key, value) -> {
            if (configRepo.findByConfigKey(key).isEmpty()) {
                configRepo.save(SiteConfig.builder().configKey(key).configValue(value).build());
            }
        });
        log.info("✅ Site config seeded");
    }

    private void seedTestimonials() {
        if (testimonialRepo.count() == 0) {
            testimonialRepo.saveAll(List.of(
                Testimonial.builder().studentName("Priya Sharma").course("MBA, Delhi University")
                    .text("AssignEase helped me score distinction in my thesis. The quality was exceptional and delivery was before deadline!")
                    .avatar("PS").rating(5).active(true).displayOrder(1).build(),
                Testimonial.builder().studentName("Rahul Mehta").course("B.Tech CSE, IIT Bombay")
                    .text("Fast delivery and clean, well-documented code. My programming assignments are always exactly what professors expect.")
                    .avatar("RM").rating(5).active(true).displayOrder(2).build(),
                Testimonial.builder().studentName("Anika Joshi").course("M.Sc Data Science, BITS")
                    .text("The data analysis report was thorough and professionally formatted. Saved me so much time during exams!")
                    .avatar("AJ").rating(5).active(true).displayOrder(3).build(),
                Testimonial.builder().studentName("James Wilson").course("MBA, London Business School")
                    .text("Excellent research quality. The writer understood exactly what my professor was looking for. Highly recommended!")
                    .avatar("JW").rating(5).active(true).displayOrder(4).build()
            ));
            log.info("✅ Default testimonials seeded");
        }
    }
}
