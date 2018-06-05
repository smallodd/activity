package com.hengtian.flow.model;

import java.util.Date;

/**
 * 任务信息-返回值
 */
public class TaskResult {

    private static final long serialVersionUID = 1L;

    /**
     * 任务节点ID
     */
    private String taskId;
    /**
     * 任务节点名称
     */
    private String taskName;
    /**
     * 任务节点状态(1.待签收   2.待受理[办理,转办,委派,跳转])
     */
    private String taskState;
    /**
     * 当前处理人
     */
    private String assignee;
    /**
     * 前一步处理人
     */
    private String assigneeBefore;
    /**
     * 后一步处理人
     */
    private String assigneeNext;
    /**
     * 委托人
     */
    private String assigneeDelegate;
    /**
     * 流程创建人
     */
    private String creator;
    /**
     * 流程创建人所属部门
     */
    private String creatorDept;
    /**
     * 流程编号
     */
    private String processInstanceId;
    /**
     * 流程标题
     */
    private String processInstanceTitle;
    /**
     * 流程定义ID
     */
    private String processDefinitionId;
    /**
     * 流程名称
     */
    private String processDefinitionName;
    /**
     * 流程状态
     */
    private String processInstanceState;
    /**
     * 创建时间
     */
    private Date startTime;
    /**
     * 结束时间
     */
    private Date endTime;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskState() {
        return taskState;
    }

    public void setTaskState(String taskState) {
        this.taskState = taskState;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getAssigneeBefore() {
        return assigneeBefore;
    }

    public void setAssigneeBefore(String assigneeBefore) {
        this.assigneeBefore = assigneeBefore;
    }

    public String getAssigneeNext() {
        return assigneeNext;
    }

    public void setAssigneeNext(String assigneeNext) {
        this.assigneeNext = assigneeNext;
    }

    public String getAssigneeDelegate() {
        return assigneeDelegate;
    }

    public void setAssigneeDelegate(String assigneeDelegate) {
        this.assigneeDelegate = assigneeDelegate;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getCreatorDept() {
        return creatorDept;
    }

    public void setCreatorDept(String creatorDept) {
        this.creatorDept = creatorDept;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public String getProcessInstanceTitle() {
        return processInstanceTitle;
    }

    public void setProcessInstanceTitle(String processInstanceTitle) {
        this.processInstanceTitle = processInstanceTitle;
    }

    public String getProcessDefinitionName() {
        return processDefinitionName;
    }

    public void setProcessDefinitionName(String processDefinitionName) {
        this.processDefinitionName = processDefinitionName;
    }

    public String getProcessInstanceState() {
        return processInstanceState;
    }

    public void setProcessInstanceState(String processInstanceState) {
        this.processInstanceState = processInstanceState;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public String getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }
}
