package com.hengtian.flow.controller;

import com.hengtian.common.operlog.SysLog;
import com.hengtian.common.param.TaskQueryParam;
import com.hengtian.common.param.TaskRemindQueryParam;
import com.hengtian.enquire.service.EnquireService;
import com.hengtian.flow.service.RemindTaskService;
import com.hengtian.flow.service.WorkflowService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Created by ma on 2018/4/17.
 * 所有列表查询都放这里
 */
@Controller
public class WorkflowQueryController {

    @Autowired
    private RemindTaskService remindTaskService;
    @Autowired
    private EnquireService enquireService;

    @Autowired
    private WorkflowService workflowService;

    /**
     * 催办任务列表
     *
     * @param taskRemindQueryParam 任务查询条件实体类
     * @return json
     * @author houjinrong@chtwm.com
     * date 2018/4/19 15:17
     */
    @ResponseBody
    @SysLog("催办任务列表")
    @ApiOperation(httpMethod = "POST", value = "催办任务列表")
    @RequestMapping(value = "/rest/task/remind/page", method = RequestMethod.POST)
    public Object remindTaskList(@ApiParam(value = "任务查询条件", name = "taskQueryParam", required = true) @RequestBody TaskRemindQueryParam taskRemindQueryParam) {
        return remindTaskService.remindTaskList(taskRemindQueryParam);
    }

    /**
     * 被催办任务列表
     *
     * @param taskRemindQueryParam 任务查询条件实体类
     * @return json
     * @author houjinrong@chtwm.com
     * date 2018/4/19 15:17
     */
    @ResponseBody
    @SysLog("被催办任务列表")
    @ApiOperation(httpMethod = "POST", value = "被催办任务列表")
    @RequestMapping(value = "/rest/task/reminded/page", method = RequestMethod.POST)
    public Object remindedTaskList(@ApiParam(value = "任务查询条件", name = "taskQueryParam", required = true) @RequestBody TaskRemindQueryParam taskRemindQueryParam) {
        return remindTaskService.remindedTaskList(taskRemindQueryParam);
    }

    /**
     * 未办任务列表
     *
     * @param taskQueryParam 任务查询条件实体类
     * @return json
     * @author houjinrong@chtwm.com
     * date 2018/4/19 15:17
     */
    @ResponseBody
    @SysLog("未办任务列表")
    @ApiOperation(httpMethod = "POST", value = "未办任务列表")
    @RequestMapping(value = "/rest/task/open/page", method = RequestMethod.POST)
    public Object openTaskList(@ApiParam(value = "任务查询条件", name = "taskQueryParam", required = true) @RequestBody TaskQueryParam taskQueryParam) {
        return workflowService.openTaskList(taskQueryParam);
    }

    /**
     * 已办任务列表
     *
     * @param taskQueryParam 任务查询条件实体类
     * @return json
     * @author houjinrong@chtwm.com
     * date 2018/4/19 15:17
     */
    @ResponseBody
    @SysLog("已办任务列表")
    @ApiOperation(httpMethod = "POST", value = "已办任务列表")
    @RequestMapping(value = "/rest/task/close/page", method = RequestMethod.POST)
    public Object closeTaskList(@ApiParam(value = "任务查询条件", name = "taskQueryParam", required = true) @RequestBody TaskQueryParam taskQueryParam) {
        return workflowService.closeTaskList(taskQueryParam);
    }

    /**
     * 待处理任务列表
     * @param taskQueryParam 任务查询条件实体类
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/23 16:35
     */
    @ResponseBody
    @SysLog("待处理任务列表")
    @ApiOperation(httpMethod = "POST", value = "待处理任务列表")
    @RequestMapping(value = "/rest/task/close/page", method = RequestMethod.POST)
    public Object activeTaskList(@ApiParam(value = "任务查询条件", name = "taskQueryParam", required = true) @RequestBody TaskQueryParam taskQueryParam){
        return workflowService.activeTaskList(taskQueryParam);
    }

    /**
     * 待签收任务列表
     * @param taskQueryParam 任务查询条件实体类
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/23 16:35
     */
    @ResponseBody
    @SysLog("待签收任务列表")
    @ApiOperation(httpMethod = "POST", value = "待签收任务列表")
    @RequestMapping(value = "/rest/task/close/page", method = RequestMethod.POST)
    public Object claimTaskList(@ApiParam(value = "任务查询条件", name = "taskQueryParam", required = true) @RequestBody TaskQueryParam taskQueryParam){
        return workflowService.claimTaskList(taskQueryParam);
    }

    /**
     * 问询任务列表
     *
     * @param taskQueryParam 任务查询条件实体类
     * @return
     */
    @ResponseBody
    @SysLog("问询任务列表")
    @ApiOperation(httpMethod = "POST", value = "问询任务列表")
    @RequestMapping(value = "/rest/task/enquire/page", method = RequestMethod.POST)
    public Object enquireTaskList(@ApiParam(value = "任务查询条件", name = "taskQueryParam", required = true) @RequestBody TaskQueryParam taskQueryParam) {
        return enquireService.enquireTaskList(taskQueryParam);
    }


    /**
     * 被问询任务列表
     *
     * @param taskQueryParam 任务查询条件实体类
     * @return
     */
    @ResponseBody
    @SysLog("被问询任务列表")
    @ApiOperation(httpMethod = "POST", value = "被问询任务列表")
    @RequestMapping(value = "/rest/task/enquired/page", method = RequestMethod.POST)
    public Object enquiredTaskList(@ApiParam(value = "任务查询条件", name = "taskQueryParam", required = true) @RequestBody TaskQueryParam taskQueryParam) {
        return enquireService.enquiredTaskList(taskQueryParam);
    }

    /**
     * 问询意见查询接口
     *
     * @param userId 操作人ID
     * @param taskId 任务ID
     * @return
     */
    @ResponseBody
    @SysLog("问询意见查询接口")
    @ApiOperation(httpMethod = "POST", value = "问询意见查询接口")
    @RequestMapping(value = "/rest/task/enquire/comment", method = RequestMethod.POST)
    public Object enquireComment(@ApiParam(value = "操作人ID", name = "userId", required = true) String userId, @ApiParam(value = "任务ID", name = "taskId", required = true) String taskId) {
        return workflowService.enquireComment(userId, taskId);
    }
}
