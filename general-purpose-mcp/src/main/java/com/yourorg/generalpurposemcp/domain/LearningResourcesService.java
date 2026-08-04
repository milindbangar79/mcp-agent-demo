package com.yourorg.generalpurposemcp.domain;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class LearningResourcesService {

    public record Resource(String name, String url, String description) {}

    public record LearningResources(
            String topic,
            List<Resource> calculators,
            List<Resource> articles,
            List<Resource> trainingContent) {}

    public LearningResources findResources(String topic) {
        return new LearningResources(
                topic,
                List.of(
                        new Resource(
                                "Calculator.net Simple Interest Calculator",
                                "https://www.calculator.net/simple-interest-calculator.html",
                                "Quick online calculator with principal, rate, and time inputs"),
                        new Resource(
                                "Bankrate Simple Interest Calculator",
                                "https://www.bankrate.com/loans/personal-loans/simple-interest-calculator/",
                                "Includes amortization breakdown and comparison charts"),
                        new Resource(
                                "Omni Calculator – Simple Interest",
                                "https://www.omnicalculator.com/finance/simple-interest",
                                "Visual step-by-step breakdown with formula explanation")),
                List.of(
                        new Resource(
                                "Simple Interest – Investopedia",
                                "https://www.investopedia.com/terms/s/simple_interest.asp",
                                "Definition, formula, worked examples, and comparison with compound interest"),
                        new Resource(
                                "Simple vs Compound Interest – Khan Academy",
                                "https://www.khanacademy.org/economics-finance-domain/core-finance/interest-tutorial",
                                "Explains the difference and when each applies"),
                        new Resource(
                                "Simple Interest Formula – Math is Fun",
                                "https://www.mathsisfun.com/money/simple-interest.html",
                                "Plain-English explanation with interactive examples")),
                List.of(
                        new Resource(
                                "Khan Academy – Interest and Debt",
                                "https://www.khanacademy.org/economics-finance-domain/core-finance/interest-tutorial",
                                "Free video course covering simple interest, compound interest, and APR"),
                        new Resource(
                                "Coursera – Finance for Non-Finance Professionals",
                                "https://www.coursera.org/learn/finance-for-non-finance-managers",
                                "Beginner course covering interest calculations in real-world context"),
                        new Resource(
                                "YouTube – Simple Interest Explained (Professor Leonard)",
                                "https://www.youtube.com/results?search_query=simple+interest+professor+leonard",
                                "Concise video walkthrough of the SI formula with examples")));
    }
}