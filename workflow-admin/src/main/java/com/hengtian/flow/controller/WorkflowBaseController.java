package com.hengtian.flow.controller;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hengtian.common.base.BaseController;
import com.hengtian.common.enums.AssignType;
import com.hengtian.flow.model.TRuTask;
import com.hengtian.flow.service.TRuTaskService;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.pvm.PvmTransition;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.task.Task;
import org.activiti.spring.ProcessEngineFactoryBean;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class WorkflowBaseController extends BaseController {

    Logger logger = Logger.getLogger(getClass());

    @Autowired
    ProcessEngineFactoryBean processEngine;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TRuTaskService tRuTaskService;


    /**
     * 获取需要高亮的线 (适配5.18以上版本；由于mysql5.6.4之后版本时间支持到毫秒，固旧方法比较开始时间的方法不在适合当前系统)
     *
     * @param processDefinitionEntity
     * @param historicActivityInstances
     * @return
     */
    protected List<String> getHighLightedFlows(
            ProcessDefinitionEntity processDefinitionEntity,
            List<HistoricActivityInstance> historicActivityInstances) {
        List<String> highFlows = new ArrayList<String>();// 用以保存高亮的线flowId
        for (int i = 0; i < historicActivityInstances.size() - 1; i++) {// 对历史流程节点进行遍历
            HistoricActivityInstance hai = historicActivityInstances.get(i);
            ActivityImpl activityImpl = processDefinitionEntity.findActivity(hai.getActivityId());// 得到节点定义的详细信息
            List<ActivityImpl> sameStartTimeNodes = new ArrayList<ActivityImpl>();// 用以保存后需开始时间相同的节点

            for (int j = i + 1; j < historicActivityInstances.size(); j++) {
                HistoricActivityInstance activityImpl1 = historicActivityInstances.get(j);// 后续第一个节点
                if (hai.getEndTime() != null && activityImpl1.getStartTime().getTime()-hai.getEndTime().getTime() < 1000) {
                    // 如果第一个节点和第二个节点开始时间相同保存
                    ActivityImpl sameActivityImpl2 = processDefinitionEntity.findActivity(activityImpl1.getActivityId());
                    sameStartTimeNodes.add(sameActivityImpl2);
                }
            }
            List<PvmTransition> pvmTransitions = activityImpl.getOutgoingTransitions();// 取出节点的所有出去的线
            for (PvmTransition pvmTransition : pvmTransitions) {
                // 对所有的线进行遍历
                ActivityImpl pvmActivityImpl = (ActivityImpl) pvmTransition.getDestination();
                // 如果取出的线的目标节点存在时间相同的节点里，保存该线的id，进行高亮显示
                if (sameStartTimeNodes.contains(pvmActivityImpl)) {
                    highFlows.add(pvmTransition.getId());
                }
            }
        }
        return highFlows;
    }

    /**
     * 获取任务办理人
     * @param taskId 任务ID
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/5/18 15:58
     */
    protected Set<String> getAssigneeUserByTaskId(String taskId){
        if(StringUtils.isBlank(taskId)){
            return null;
        }

        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if(task == null){
            return null;
        }

        List<String> assigneeList = Lists.newArrayList();
        if(StringUtils.isNotBlank(task.getAssignee())){
            String assignee = task.getAssignee().replaceAll("_N","").replaceAll("_Y","");
            assigneeList = Arrays.asList(assignee.split(","));
        }

        EntityWrapper<TRuTask> wrapper = new EntityWrapper<>();
        wrapper.where("task_id={0}", taskId);
        List<TRuTask> tRuTasks = tRuTaskService.selectList(wrapper);
        Set<String> result = Sets.newHashSet();
        for(TRuTask t : tRuTasks){
            if(StringUtils.isNotBlank(t.getAssigneeReal())){
                String[] array = t.getAssigneeReal().split(",");
                for(String a : array){
                    if(!assigneeList.contains(a)){
                        result.add(a);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 获取任务办理人
     * @param taskId 任务ID
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/5/18 15:58
     */
    protected JSONArray getAssigneeUserTreeByTaskId(String taskId){
        if(StringUtils.isBlank(taskId)){
            return null;
        }

        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if(task == null){
            return null;
        }

        JSONArray json = new JSONArray();

        EntityWrapper<TRuTask> wrapper = new EntityWrapper<>();
        wrapper.where("task_id={0}", taskId);
        List<TRuTask> tRuTasks = tRuTaskService.selectList(wrapper);
        for(TRuTask t : tRuTasks){
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id", t.getAssignee());
            jsonObject.put("text", t.getAssigneeName());
            if(AssignType.ROLE.code.equals(t.getAssigneeType())){
                if(StringUtils.isNotBlank(t.getAssigneeReal())){
                    String[] array = t.getAssigneeReal().split(",");
                    for(String a : array){
                        JSONObject child = new JSONObject();
                        child.put("id", t.getAssignee()+":"+a);
                        child.put("text", a);
                        if(!jsonObject.containsKey("children")){
                            JSONArray jsonArray = new JSONArray();
                            jsonArray.add(child);
                            jsonObject.put("children", jsonArray);
                        }else{
                            jsonObject.accumulate("children", child);
                        }
                    }
                }
                if(jsonObject.containsKey("children")){
                    json.add(jsonObject);
                }
            }else {
                json.add(jsonObject);
            }
        }

        return json;
    }
}
