package com.assignease.config;

import com.assignease.entity.BlogPost;
import com.assignease.entity.StudentFeedback;
import com.assignease.entity.SiteConfig;
import com.assignease.entity.Testimonial;
import com.assignease.entity.User;
import com.assignease.repository.BlogPostRepository;
import com.assignease.repository.StudentFeedbackRepository;
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

    private final BlogPostRepository blogPostRepository;
    private final StudentFeedbackRepository studentFeedbackRepository;
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
        if (!userRepository.existsByEmail("admin@edupilothelp.com")) {
            User admin = User.builder()
                .fullName("Super Admin").email("admin@edupilothelp.com")
                .password(passwordEncoder.encode("Admin@123"))
                .role(User.Role.ROLE_ADMIN).enabled(true).firstLogin(false)
                .build();
            userRepository.save(admin);
            log.info("✅ Default admin: admin@edupilothelp.com / Admin@123");
        }
    }

    private void seedSiteConfig() {
        Map<String, String> defaults = Map.of(
            "company_name",    "EduAssist",
            "company_tagline", "Academic Excellence Delivered to You",
            "company_email",   "support@assignease.com",
            "company_phone",   "+91 98765 43210",
            "company_address", "Austin, Texas, USA",
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
                Testimonial.builder().studentName("Sarah Mitchell").course("MBA, Boston University")
                    .text("EduAssist helped me score distinction in my thesis. The quality was exceptional and delivery was before deadline!")
                    .avatar("SM").rating(5).active(true).displayOrder(1).build(),
                Testimonial.builder().studentName("Tyler Johnson").course("B.Sc Computer Science, UCLA")
                    .text("Fast delivery and clean, well-documented code. My programming assignments are always exactly what professors expect.")
                    .avatar("TJ").rating(5).active(true).displayOrder(2).build(),
                Testimonial.builder().studentName("Emma Clarke").course("M.Sc Data Science, NYU")
                    .text("The data analysis report was thorough and professionally formatted. Saved me so much time during exams!")
                    .avatar("EC").rating(5).active(true).displayOrder(3).build(),
                Testimonial.builder().studentName("James Wilson").course("MBA, London Business School")
                    .text("Excellent research quality. The writer understood exactly what my professor was looking for. Highly recommended!")
                    .avatar("JW").rating(5).active(true).displayOrder(4).build()
            ));
            log.info("✅ Default testimonials seeded");
        }
        seedBlogs();
        seedFeedback();
    }

    private void seedBlogs() {
        if (blogPostRepository.count() > 0) return;
        java.util.List<BlogPost> posts = new java.util.ArrayList<>();
        posts.add(BlogPost.builder().title("Pay Someone To Take My Online Class: Is It Safe And Worth It?").slug("pay-someone-take-online-class-safe").excerpt("Millions of students search for ways to manage their online coursework. Here's everything you need to know about hiring academic experts to help with your online class.").content("<h2>What Does Paying Someone To Take My Online Class Mean?</h2><p>When students search for someone to take their online class, they are looking for a qualified academic expert who can log into their LMS and handle all coursework — quizzes, assignments, discussion posts, exams, and overall grade management.</p><h2>Is It Safe?</h2><p>Safety depends entirely on the provider. EduAssist uses 256-bit SSL encryption. Your credentials are only accessible to your assigned expert and the admin quality team. We never share your identity with writers and delete all credentials after class completion.</p><h2>How Much Does It Cost?</h2><p>Pricing starts from $42/week for standard coursework. Always look for transparent pricing and installment plans — never pay everything upfront before seeing any work.</p><h2>What To Look For</h2><ul><li>Verified subject-matter experts with proven credentials</li><li>Pay-as-you-go installment plans</li><li>Admin quality review before deliveries</li><li>256-bit SSL encryption and confidentiality guarantees</li><li>Unlimited revisions policy</li><li>24/7 customer support with fast response times</li></ul>").category("Online Class Help").author("EduAssist Team").readTimeMinutes(7).published(true).featured(true).build());
        posts.add(BlogPost.builder().title("How To Do Well In Online Classes: 10 Proven Strategies").slug("how-to-do-well-in-online-classes").excerpt("Online classes can be challenging without face-to-face support. Discover the top 10 proven strategies to excel in any online course.").content("<h2>Why Online Classes Are Challenging</h2><p>Online classes require significantly more self-discipline than traditional courses. Without scheduled attendance, many students struggle with time management and motivation.</p><h2>1. Create a Dedicated Study Schedule</h2><p>Treat your online class like a physical class. Block out specific hours each week dedicated exclusively to coursework. Consistency is the single most powerful predictor of success.</p><h2>2. Engage Actively in Discussion Forums</h2><p>Participation in discussion boards is often graded. Log in at least three times per week to post original responses and reply meaningfully to at least two classmates.</p><h2>3. Read Every Syllabus Carefully</h2><p>The syllabus contains your roadmap for the entire semester. Note every deadline, grading weight, and professor expectation before the first week ends.</p><h2>4. Use the LMS Dashboard Every Day</h2><p>Log into Canvas, Blackboard, or your institution's LMS every single day. Professors post announcements and new assignments that are easy to miss if you check infrequently.</p><h2>5. Reach Out For Help Early</h2><p>If you fall behind or find coursework overwhelming, act immediately. Whether that means emailing your professor or exploring professional academic support — waiting until the last week never ends well.</p>").category("Study Tips").author("EduAssist Team").readTimeMinutes(8).published(true).featured(true).build());
        posts.add(BlogPost.builder().title("Can You Pay Someone To Do Your Online Class? Everything You Need To Know").slug("can-you-pay-someone-do-your-online-class").excerpt("Yes, thousands of students hire qualified experts to handle their online classes every semester. But how does it work, what does it cost, and what should you look for?").content("<h2>The Short Answer</h2><p>Yes — thousands of students across the USA, UK, Canada and Australia hire qualified academic experts to handle their online classes every semester. The practice is far more widespread than most people realize, particularly among working adults and students juggling multiple courses.</p><h2>How The Process Works</h2><p>You submit your class details — institution name, portal URL, class dates, and subject area. The service assigns a verified expert who logs into your LMS and handles all coursework. Work is reviewed by an admin quality team before each deliverable is packaged for you.</p><h2>How Much Does It Cost?</h2><p>Pricing typically ranges from $42 to $120+ per week depending on subject complexity, credit hours, and workload. Reputable services offer installment plans so you only pay as work is completed and approved.</p><h2>Is It Worth It?</h2><p>For students balancing full-time work, caregiving, or multiple challenging courses simultaneously, the ROI is often significant. Maintaining a strong GPA while managing real-world commitments has measurable career impact that far exceeds the cost of professional support.</p>").category("Online Class Help").author("EduAssist Team").readTimeMinutes(6).published(true).featured(false).build());
        posts.add(BlogPost.builder().title("Best Online Class Help Services in USA 2024: A Complete Guide").slug("best-online-class-help-services-usa-2024").excerpt("We reviewed the top academic support services available to USA students in 2024. Find the most reliable, affordable, and secure options.").content("<h2>What To Look For In An Online Class Help Service</h2><p>With hundreds of services claiming to offer online class assistance, knowing what separates legitimate providers from low-quality ones is essential.</p><h2>Top Criteria We Evaluated</h2><ul><li>Expert verification process — do they test subject knowledge?</li><li>Payment security — Stripe or equivalent, never crypto-only</li><li>Installment plan availability — pay as work is delivered</li><li>Admin quality review before delivery</li><li>SSL encryption and data deletion policies</li><li>Response time and support channel availability</li></ul><h2>EduAssist</h2><p>EduAssist stands out for its verified experts, transparent installment pricing starting from $42/week, admin quality review on every deliverable, and 256-bit SSL encryption. Students receive login credentials immediately after submitting their class details.</p><h2>What Red Flags To Avoid</h2><p>Avoid any service that demands full payment upfront, offers suspiciously low prices, has no verifiable reviews, or asks you to pay via cryptocurrency exclusively.</p>").category("Online Class Help").author("EduAssist Team").readTimeMinutes(6).published(true).featured(false).build());
        posts.add(BlogPost.builder().title("Top LMS Platforms For Online Classes: Canvas vs Blackboard vs Moodle").slug("top-lms-platforms-online-classes").excerpt("Canvas, Blackboard, Moodle, D2L — which LMS platform does your school use and what should you know about navigating it effectively?").content("<h2>What Is An LMS?</h2><p>A Learning Management System (LMS) is the platform your institution uses to host online courses. Understanding your LMS is the first step to succeeding in any online class.</p><h2>Canvas</h2><p>Canvas is widely used by US universities and known for its clean interface. It features modules, quizzes with auto-grading, discussion boards, SpeedGrader, and integrated video tools. Most professors post all assignments inside Modules.</p><h2>Blackboard Learn</h2><p>Blackboard is one of the oldest LMS platforms and is common in large public universities. It can feel complex but all coursework, grade tracking, and communication tools are accessible from the main menu.</p><h2>Moodle</h2><p>Moodle is open-source and highly customizable. Many community colleges and international institutions use it. Assignments, quizzes, forums, and resources are organized by week or topic blocks.</p><h2>D2L Brightspace</h2><p>D2L Brightspace is gaining popularity for its adaptive learning features. The layout is intuitive and the mobile app is excellent for checking grades and announcements on the go.</p><h2>Which LMS Do Our Experts Support?</h2><p>EduAssist experts are trained on all major LMS platforms — Canvas, Blackboard, Moodle, D2L Brightspace, Coursera, edX, McGraw-Hill Connect, Pearson MyLab, and more. Whatever platform your institution uses, we navigate it flawlessly.</p>").category("Online Class Help").author("EduAssist Team").readTimeMinutes(5).published(true).featured(false).build());
        blogPostRepository.saveAll(posts);
        log.info("✅ Blog posts seeded: {} articles", posts.size());
    }

    private void seedFeedback() {
        if (studentFeedbackRepository.count() > 0) return;
        java.util.List<StudentFeedback> list = java.util.List.of(
            StudentFeedback.builder().studentName("Marcus Thompson").course("MBA — Operations Management").location("Texas, USA").feedbackText("I was working full-time and taking two online courses. EduAssist handled both and I got A grades in both. The expert was always ahead of deadlines and the admin team was incredibly responsive throughout the semester.").rating(5).avatar("MT").visible(true).build(),
            StudentFeedback.builder().studentName("Sarah Mitchell").course("Computer Science — Java Programming").location("California, USA").feedbackText("My Java programming class was completely overwhelming. The expert completed every lab, passed all autograder tests and even left comments in the code so I could follow along. Genuinely an incredible service worth every cent.").rating(5).avatar("SM").visible(true).build(),
            StudentFeedback.builder().studentName("Jessica Williams").course("Nursing — Healthcare Management").location("Florida, USA").feedbackText("As an international student juggling work and family, I needed reliable help with my nursing coursework. EduAssist handled every discussion post and written paper. Delivered before every single deadline without fail.").rating(5).avatar("JW").visible(true).build(),
            StudentFeedback.builder().studentName("James Richardson").course("Business Law — Contract Law").location("New York, USA").feedbackText("The pay-as-you-go installment plan made it genuinely affordable. I only paid after each week was delivered and reviewed. A completely transparent process from start to finish and I would highly recommend it.").rating(5).avatar("JR").visible(true).build(),
            StudentFeedback.builder().studentName("Emily Watson").course("Statistics — Applied Regression").location("Ohio, USA").feedbackText("I was skeptical at first but the process was completely transparent from day one. Got my custom plan the next morning and my expert started same day. Every assignment delivered with days to spare.").rating(5).avatar("EW").visible(true).build(),
            StudentFeedback.builder().studentName("Daniel Morrison").course("MBA — Strategic Management").location("Georgia, USA").feedbackText("Used EduAssist for a full 16-week MBA course. Weekly case studies, two major research papers, a group project and the final exam — everything came back A-grade. Absolutely worth every single cent paid.").rating(5).avatar("DM").visible(true).build()
        );
        studentFeedbackRepository.saveAll(list);
        log.info("✅ Student feedback seeded: {} reviews", list.size());
    }

}