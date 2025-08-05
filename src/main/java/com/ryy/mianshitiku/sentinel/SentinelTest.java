package com.ryy.mianshitiku.sentinel;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;

import java.util.ArrayList;
import java.util.List;

public class SentinelTest {

    public static void main(String[] args) {
        // 配置规则.
        initFlowRules();

        while (true) {
            // 自定义资源，资源名称为 "HelloWorld"
            try (Entry entry = SphU.entry("HelloWorld")) {
                // 被保护的逻辑
                System.out.println("hello world");
            } catch (BlockException ex) {
                // 处理被流控的逻辑
                System.out.println("blocked!");
            }
        }
    }

    private static void initFlowRules(){
        // 定义规则列表
        List<FlowRule> rules = new ArrayList<>();
        FlowRule rule = new FlowRule();
        // 指定规则设置的资源对象为 "HelloWorld"
        rule.setResource("HelloWorld");
        // 指定 QPS
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        // 设置每秒的访问资源的次数不得超过20次
        rule.setCount(20);

        rules.add(rule);
        FlowRuleManager.loadRules(rules);
    }
}
