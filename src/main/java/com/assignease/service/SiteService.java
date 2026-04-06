package com.assignease.service;

import com.assignease.entity.SiteConfig;
import com.assignease.entity.Testimonial;
import com.assignease.entity.TextMeLead;
import com.assignease.repository.SiteConfigRepository;
import com.assignease.repository.TestimonialRepository;
import com.assignease.repository.TextMeLeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteService {

    private final SiteConfigRepository configRepo;
    private final TestimonialRepository testimonialRepo;
    private final TextMeLeadRepository leadRepo;

    // ── Site Config ──────────────────────────────────────────────────────────

    public Map<String, String> getAllConfig() {
        Map<String, String> result = new HashMap<>();
        configRepo.findAll().forEach(c -> result.put(c.getConfigKey(), c.getConfigValue()));
        return result;
    }

    public String getConfig(String key) {
        return configRepo.findByConfigKey(key)
                .map(SiteConfig::getConfigValue)
                .orElse("");
    }

    public void setConfig(String key, String value) {
        SiteConfig config = configRepo.findByConfigKey(key)
                .orElse(SiteConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        configRepo.save(config);
    }

    public void updateConfigs(Map<String, String> configs) {
        configs.forEach(this::setConfig);
    }

    // ── Testimonials ─────────────────────────────────────────────────────────

    public List<Testimonial> getActiveTestimonials() {
        return testimonialRepo.findByActiveTrueOrderByDisplayOrderAsc();
    }

    public List<Testimonial> getAllTestimonials() {
        return testimonialRepo.findAll();
    }

    public Testimonial saveTestimonial(Testimonial t) {
        return testimonialRepo.save(t);
    }

    public void deleteTestimonial(Long id) {
        testimonialRepo.deleteById(id);
    }

    public Testimonial toggleTestimonialActive(Long id) {
        Testimonial t = testimonialRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Testimonial not found"));
        t.setActive(!t.getActive());
        return testimonialRepo.save(t);
    }

    // ── Text Me Leads ────────────────────────────────────────────────────────

    public TextMeLead saveTextMeLead(String phoneNumber, String countryCode) {
        // Update if already exists
        if (leadRepo.existsByPhoneNumber(phoneNumber)) {
            log.info("Duplicate text-me lead: {}", phoneNumber);
        }
        TextMeLead lead = TextMeLead.builder()
                .phoneNumber(phoneNumber)
                .countryCode(countryCode)
                .status(TextMeLead.LeadStatus.NEW)
                .build();
        return leadRepo.save(lead);
    }

    public List<TextMeLead> getAllLeads() {
        return leadRepo.findAllByOrderByCreatedAtDesc();
    }

    public List<TextMeLead> getNewLeads() {
        return leadRepo.findByStatusOrderByCreatedAtDesc(TextMeLead.LeadStatus.NEW);
    }

    public TextMeLead updateLeadStatus(Long id, String status, String notes) {
        TextMeLead lead = leadRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead not found"));
        lead.setStatus(TextMeLead.LeadStatus.valueOf(status));
        if (notes != null) lead.setNotes(notes);
        if (status.equals("CONTACTED")) lead.setContactedAt(LocalDateTime.now());
        return leadRepo.save(lead);
    }
}
