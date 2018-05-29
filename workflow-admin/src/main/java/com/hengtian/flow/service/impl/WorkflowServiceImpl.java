package com.hengtian.flow.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hengtian.application.model.AppModel;
import com.hengtian.application.service.AppModelService;
import com.hengtian.common.enums.*;
import com.hengtian.common.param.ProcessParam;
import com.hengtian.common.param.TaskParam;
import com.hengtian.common.param.TaskQueryParam;
import com.hengtian.common.result.Constant;
import com.hengtian.common.result.Result;
import com.hengtian.common.result.TaskNodeResult;
import com.hengtian.common.utils.ConstantUtils;
import com.hengtian.common.utils.PageInfo;
import com.hengtian.common.workflow.cmd.CreateCmd;
import com.hengtian.common.workflow.cmd.JumpCmd;
import com.hengtian.common.workflow.exception.WorkFlowException;
import com.hengtian.flow.dao.WorkflowDao;
import com.hengtian.flow.model.*;
import com.hengtian.flow.service.*;
import com.hengtian.flow.vo.AskCommentDetailVo;
import com.hengtian.flow.vo.TaskVo;
import com.rbac.entity.RbacRole;
import com.rbac.entity.RbacUser;
import com.rbac.service.PrivilegeService;
import com.rbac.service.UserService;
import com.user.entity.emp.Emp;
import com.user.service.emp.EmpService;
import org.activiti.bpmn.model.FlowNode;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.NativeHistoricTaskInstanceQuery;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.NativeTaskQuery;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class WorkflowServiceImpl extends ActivitiUtilServiceImpl implements WorkflowService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private ManagementService managementService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private RemindTaskService remindTaskService;

    @Autowired
    private TAskTaskService tAskTaskService;

    @Autowired
    private AppModelService appModelService;

    @Autowired
    private RuProcinstService ruProcinstService;

    @Autowired
    private TUserTaskService tUserTaskService;

    @Autowired
    private TRuTaskService tRuTaskService;

    @Autowired
    private IdentityService identityService;

    @Autowired
    TWorkDetailService workDetailService;

    @Autowired
    private UserService userService;

    @Autowired
    private PrivilegeService privilegeService;

    @Autowired
    private WorkflowDao workflowDao;

    @Autowired
    private EmpService empService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result startProcessInstance(ProcessParam processParam) {
        Result result = new Result();
        String jsonVariables = processParam.getJsonVariables();
        Map<String, Object> variables = new HashMap<>();
        if (StringUtils.isNotBlank(jsonVariables)) {
            variables = JSON.parseObject(jsonVariables);
        }

        EntityWrapper<AppModel> wrapperApp = new EntityWrapper();

        wrapperApp.where("app_key={0}", processParam.getAppKey()).andNew("model_key={0}", processParam.getProcessDefinitionKey());
        AppModel appModelResult = appModelService.selectOne(wrapperApp);
        //系统与流程定义之间没有配置关系
        if (appModelResult == null) {
            log.info("系统键值：【{}】对应的modelKey:【{}】关系不存在!", processParam.getAppKey(), processParam.getProcessDefinitionKey());
            result.setCode(Constant.RELATION_NOT_EXIT);
            result.setMsg("系统键值：【" + processParam.getAppKey() + "】对应的modelKey:【" + processParam.getProcessDefinitionKey() + "】关系不存在!");
            result.setSuccess(false);
            return result;

        }
        //校验当前业务主键是否已经在系统中存在
        boolean isInFlow = checkBusinessKeyIsInFlow(processParam.getProcessDefinitionKey(), processParam.getBussinessKey(), processParam.getAppKey());

        if (isInFlow) {
            log.info("业务主键【{}】已经提交过任务", processParam.getBussinessKey());
            //已经创建过则返回错误信息
            result.setSuccess(false);
            result.setMsg("此条信息已经提交过任务");
            result.setCode(Constant.BUSSINESSKEY_EXIST);
            return result;
        } else {
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionKey(processParam.getProcessDefinitionKey()).latestVersion().singleResult();
            if(!processParam.isCustomApprover()){
                    EntityWrapper entityWrapper = new EntityWrapper();
                    entityWrapper.where("proc_def_key={0}", processParam.getProcessDefinitionKey()).andNew("version_={0}", processDefinition.getVersion()).isNull("candidate_ids");
                    //查询当前任务任务节点信息
                    TUserTask query=new TUserTask();
                    query.setProcDefKey(processParam.getProcessDefinitionKey());
                    query.setVersion(processDefinition.getVersion());
                    long count = tUserTaskService.selectNotSetAssign(query);
                    if(count>0){
                        result.setCode(Constant.TASK_NOT_SET_APPROVER);
                        result.setMsg("节点有存在未设置审批人");
                        result.setSuccess(false);
                        return result;
                    }
            }
            String creator = processParam.getCreatorId();
            variables.put("customApprover", processParam.isCustomApprover());
            variables.put("appKey", processParam.getAppKey());
            identityService.setAuthenticatedUserId(creator);
            //生成任务
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(processParam.getProcessDefinitionKey(), processParam.getBussinessKey(), variables);

            //给对应实例生成标题
            runtimeService.setProcessInstanceName(processInstance.getId(), processParam.getTitle());


            //查询创建完任务之后生成的任务信息
            List<Task> taskList = taskService.createTaskQuery().processInstanceId(processInstance.getId()).list();

            String taskId = "";
            if (!processParam.isCustomApprover()) {
                log.info("工作流平台设置审批人");

                for (int i = 0; i < taskList.size(); i++) {
                    Task task = taskList.get(i);
                    taskId += task.getId();
                    EntityWrapper entityWrapper = new EntityWrapper();
                    entityWrapper.where("proc_def_key={0}", processParam.getProcessDefinitionKey()).andNew("task_def_key={0}", task.getTaskDefinitionKey()).andNew("version_={0}", processDefinition.getVersion());
                    //查询当前任务任务节点信息
                    TUserTask tUserTask = tUserTaskService.selectOne(entityWrapper);
                    //将流程创建人暂存到expr字段
                    tUserTask.setExpr(creator);
                    boolean flag = setAssignee(task, tUserTask);
                    if(!flag){
                        throw new WorkFlowException("设置审批人异常");
                    }
                }
                result.setSuccess(true);
                result.setCode(Constant.SUCCESS);
                result.setMsg("申请成功");
                result.setObj(setButtons(TaskNodeResult.toTaskNodeResultList(taskList)));
                //存储操作记录
                TWorkDetail tWorkDetail = new TWorkDetail();
                tWorkDetail.setCreateTime(new Date());
                tWorkDetail.setDetail("工号【" + processParam.getCreatorId() + "】开启了" + processParam.getTitle() + "任务");
                tWorkDetail.setProcessInstanceId(processInstance.getProcessInstanceId());
                tWorkDetail.setOperator(processParam.getCreatorId());
                tWorkDetail.setTaskId(taskId);

                workDetailService.insert(tWorkDetail);
            } else {
                log.info("业务平台设置审批人");

                result.setSuccess(true);
                result.setCode(Constant.SUCCESS);
                result.setMsg("申请成功");
                result.setObj(setButtons(TaskNodeResult.toTaskNodeResultList(taskList)));

                TWorkDetail tWorkDetail = new TWorkDetail();
                tWorkDetail.setCreateTime(new Date());
                tWorkDetail.setDetail("工号【" + processParam.getCreatorId() + "】开启了" + processParam.getTitle() + "任务");
                tWorkDetail.setProcessInstanceId(processInstance.getProcessInstanceId());
                tWorkDetail.setOperator(processParam.getCreatorId());
                tWorkDetail.setTaskId(taskId);

                workDetailService.insert(tWorkDetail);
            }

            //添加应用-流程实例对应关系
            String deptName = "";
            RbacUser user = userService.getUserById(creator);
            if(user != null){
                deptName = user.getDeptName();
            }
            RuProcinst ruProcinst = new RuProcinst(processParam.getAppKey(), processInstance.getProcessInstanceId(), creator, deptName,processDefinition.getName());
            ruProcinstService.insert(ruProcinst);
        }
        return result;
    }

    /**
     * 设置审批人接口
     *
     * @param task
     * @param tUserTask
     */
    @Override
    public Boolean setAssignee(Task task, TUserTask tUserTask) {
        log.info("进入设置审批人接口,tUserTask参数{}", JSONObject.toJSONString(tUserTask));

        //获取任务中的自定义参数
        Map<String, Object> map = taskService.getVariables(task.getId());
        String assignees = tUserTask.getCandidateIds();
        String assigneeNames = tUserTask.getCandidateName();
        String[] assigneeArray = assignees.split(",");
        String[] assigneeNameArray = assigneeNames.split(",");
        Set set = new HashSet(Arrays.asList(assigneeArray));
        assigneeArray = (String[]) set.toArray(new String[0]);
        set = new HashSet(Arrays.asList(assigneeNameArray));
        assigneeNameArray = (String[]) set.toArray(new String[0]);

        //生成扩展任务信息
        String assignee;
        for (int i=0;i<assigneeArray.length;i++) {
            assignee = assigneeArray[i];
            TRuTask tRuTask = new TRuTask();
            tRuTask.setTaskId(task.getId());
            tRuTask.setAssignee(assignee);
            tRuTask.setAssigneeName(assigneeNameArray[i]);
            EntityWrapper entityWrapper = new EntityWrapper();
            entityWrapper.where("task_id={0}", task.getId()).andNew("assignee={0}", assignee);
            TRuTask tRu = tRuTaskService.selectOne(entityWrapper);
            if (tRu != null) {
                continue;
            }
            tRuTask.setAssigneeType(tUserTask.getAssignType());
            tRuTask.setOwer(task.getOwner());

            tRuTask.setTaskType(tUserTask.getTaskType());
            //判断如果是非人员审批，需要认领之后才能审批
            if (AssignType.ROLE.code.intValue() == tUserTask.getAssignType().intValue()) {
                tRuTask.setStatus(-1);
            } else if(AssignType.EXPR.code.intValue() == tUserTask.getAssignType().intValue()){
                //表达式
                List<Emp> empLeader = Lists.newArrayList();
                String assigneeReal = null;
                if(ExprEnum.LEADER.expr.equals(assignee)){
                    //上级节点领导
                    List<String> beforeTaskDefKeys = findBeforeTaskDefKeys(task, false);
                    if(CollectionUtils.isNotEmpty(beforeTaskDefKeys)){
                        for(String taskDefKey : beforeTaskDefKeys){
                            HistoricTaskInstance historicTaskInstance = historyService.createHistoricTaskInstanceQuery().processInstanceId(task.getProcessInstanceId()).taskDefinitionKey(taskDefKey).list().get(0);
                            String str = historicTaskInstance.getAssignee().replaceAll("_Y","").replaceAll("_N","");
                            for(String a : str.split(",")){
                                List<Emp> emps = empService.selectDirectSupervisorByCode(a);
                                if(CollectionUtils.isNotEmpty(emps)){
                                    empLeader.addAll(emps);
                                }
                            }
                        }
                    }
                }else if(ExprEnum.LEADER_CREATOR.expr.equals(assignee)){
                    //流程创建人领导
                    String creator = tUserTask.getExpr();
                    if(StringUtils.isBlank(creator)){
                        EntityWrapper<RuProcinst> wrapper = new EntityWrapper<>();
                        wrapper.where("proc_inst_id={0}", task.getProcessInstanceId());
                        RuProcinst ruProcinst = ruProcinstService.selectOne(wrapper);
                        creator = ruProcinst.getCreator();
                    }
                    List<Emp> emps = empService.selectDirectSupervisorByCode(creator);
                    if(CollectionUtils.isNotEmpty(emps)){
                        empLeader.addAll(emps);
                    }
                }

                if(CollectionUtils.isNotEmpty(empLeader)){
                    Set<String> assigneeSet = Sets.newHashSet();
                    for(Emp emp : empLeader){
                        assigneeSet.add(emp.getCode());
                    }
                    assigneeReal = StringUtils.join(assigneeSet.toArray(), ",");
                    tRuTask.setAssigneeReal(assigneeReal);

                    JSONObject approveCountJson = new JSONObject();
                    approveCountJson.put(TaskVariable.APPROVE_COUNT_TOTAL.value, assigneeSet.size());
                    approveCountJson.put(TaskVariable.APPROVE_COUNT_NEED.value, assigneeSet.size());
                    approveCountJson.put(TaskVariable.APPROVE_COUNT_NOW.value, 0);
                    approveCountJson.put(TaskVariable.APPROVE_COUNT_REFUSE.value, 0);

                    taskService.setVariableLocal(task.getId(), task.getTaskDefinitionKey()+":"+TaskVariable.APPROVE_COUNT.value,approveCountJson.toJSONString());
                }else{
                    return false;
                }
            } else{
                tRuTask.setStatus(0);
                tRuTask.setAssigneeReal(assignee);
            }
            tRuTask.setExpireTime(task.getDueDate());
            tRuTask.setAppKey(Integer.valueOf(map.get("appKey").toString()));
            tRuTask.setProcInstId(task.getProcessInstanceId());
            tRuTask.setTaskDefKey(task.getTaskDefinitionKey());
            tRuTaskService.insert(tRuTask);
        }

        log.info("设置审批人结束");
        return true;
    }

    /**
     * 审批任务
     *
     * @param task
     * @param taskParam
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Object approveTask(Task task, TaskParam taskParam){
        if(StringUtils.isBlank(taskParam.getAssignee())){
            return new Result("审批人不合法");
        }

        log.info("审批接口进入，传入参数taskParam{}", JSONObject.toJSONString(taskParam));
        Result result = new Result();
        result.setCode(Constant.SUCCESS);
        EntityWrapper entityWrapper = new EntityWrapper();
        entityWrapper.where("task_id={0}", task.getId());
        entityWrapper.like("assignee_real", "%" + taskParam.getAssignee() + "%");
        //查询流程定义信息
        ProcessDefinition processDefinition = repositoryService.getProcessDefinition(task.getProcessDefinitionId());

        String jsonVariables = taskParam.getJsonVariables();
        Map<String, Object> variables = Maps.newHashMap();
        if (StringUtils.isNotBlank(jsonVariables)) {
            variables = JSON.parseObject(jsonVariables);
        }
        //获取任务参数
        Map map = taskService.getVariables(task.getId());
        map.putAll(variables);
        TRuTask ruTask = tRuTaskService.selectOne(entityWrapper);
        if(ruTask.getAssigneeReal().indexOf(taskParam.getAssignee()) < 0){
            return new Result("审批人不合法");
        }
        if (ruTask == null) {
            result.setMsg("该用户没有操作此任务的权限");
            result.setCode(Constant.TASK_NOT_BELONG_USER);
            return result;
        } else {
            if (!ruTask.getAssigneeType().equals(taskParam.getAssignType())) {
                result.setMsg("审批人类型参数错误！");
                result.setCode(Constant.PARAM_ERROR);
                return result;
            }
            if (!ruTask.getTaskType().equals(taskParam.getTaskType())) {
                result.setMsg("任务类型参数错误！");
                result.setCode(Constant.PARAM_ERROR);
                return result;
            }
            if (taskParam.getPass() != TaskStatusEnum.COMPLETE_AGREE.status && taskParam.getPass() != TaskStatusEnum.COMPLETE_REFUSE.status) {
                result.setMsg("任务类型参数错误！");
                result.setCode(Constant.PARAM_ERROR);
                return result;
            }
            Task t = taskService.createTaskQuery().taskId(task.getId()).singleResult();
            //查询审批时该节点的执行execution
            ExecutionEntity execution= (ExecutionEntity) runtimeService.createExecutionQuery().executionId(t.getExecutionId()).singleResult();
            EntityWrapper wrapper = new EntityWrapper();
            wrapper.where("task_def_key={0}", task.getTaskDefinitionKey()).andNew("version_={0}", processDefinition.getVersion()).andNew("proc_def_key={0}", processDefinition.getKey());

            TUserTask tUserTask = tUserTaskService.selectOne(wrapper);
            identityService.setAuthenticatedUserId(taskParam.getAssignee());
            taskService.addComment(taskParam.getTaskId(), task.getProcessInstanceId(), taskParam.getComment());
            taskService.setVariables(task.getId(), map);

            //设置操作的明细备注
            TWorkDetail tWorkDetail = new TWorkDetail();
            tWorkDetail.setTaskId(task.getId());
            tWorkDetail.setOperator(taskParam.getAssignee());
            tWorkDetail.setProcessInstanceId(task.getProcessInstanceId());
            tWorkDetail.setCreateTime(new Date());
            tWorkDetail.setDetail("工号【" + taskParam.getAssignee() + "】审批了该任务，审批意见是【" + taskParam.getComment() + "】");
            workDetailService.insert(tWorkDetail);

            if (TaskType.COUNTERSIGN.value.equals(tUserTask.getTaskType()) || AssignType.EXPR.code.equals(tUserTask.getAssignType())) {
                //会签
                JSONObject approveCountJson = new JSONObject();
                String approveCount = (String)map.get(task.getTaskDefinitionKey()+":"+TaskVariable.APPROVE_COUNT.value);
                if(StringUtils.isBlank(approveCount)){
                    approveCountJson.put(TaskVariable.APPROVE_COUNT_TOTAL.value, tUserTask.getUserCountTotal());
                    approveCountJson.put(TaskVariable.APPROVE_COUNT_NEED.value, tUserTask.getUserCountNeed());
                    approveCountJson.put(TaskVariable.APPROVE_COUNT_NOW.value, 0);
                    approveCountJson.put(TaskVariable.APPROVE_COUNT_REFUSE.value, 0);
                }else {
                    approveCountJson = JSONObject.parseObject(approveCount);
                }

                int approveCountNow = approveCountJson.getInteger(TaskVariable.APPROVE_COUNT_NOW.value);
                int approveCountTotal = approveCountJson.getInteger(TaskVariable.APPROVE_COUNT_TOTAL.value);
                int approveCountNeed = approveCountJson.getInteger(TaskVariable.APPROVE_COUNT_NEED.value);
                int approveCountRefuse = approveCountJson.getInteger(TaskVariable.APPROVE_COUNT_REFUSE.value);

                if(taskParam.getPass() == TaskStatusEnum.COMPLETE_REFUSE.status){
                    approveCountRefuse ++;
                }

                approveCountJson.put(TaskVariable.APPROVE_COUNT_NOW.value,++approveCountNow);
                approveCountJson.put(TaskVariable.APPROVE_COUNT_REFUSE.value,approveCountRefuse);
                taskService.setVariableLocal(task.getId(), task.getTaskDefinitionKey()+":"+TaskVariable.APPROVE_COUNT.value,approveCountJson.toJSONString());

                int approveCountAgree = approveCountNow - approveCountRefuse;
                if(approveCountAgree >= approveCountNeed){
                    //------------任务完成-通过------------
                    String assignee = task.getAssignee();
                    taskService.setAssignee(task.getId(),StringUtils.isBlank(assignee)?(taskParam.getAssignee()+"_Y"):(assignee+","+taskParam.getAssignee()+"_Y"));
                    taskService.complete(task.getId(), map);
                    if(AssignType.PERSON.code.equals(taskParam.getAssignType())){
                        TRuTask tRuTask = new TRuTask();
                        tRuTask.setStatus(TaskStatusEnum.SKIP.status);
                        EntityWrapper wrapper_ = new EntityWrapper();
                        wrapper_.where("task_id={0}", t.getId()).andNew("status={0}", TaskStatusEnum.OPEN.status);
                        tRuTaskService.update(tRuTask, wrapper_);

                        wrapper_ = new EntityWrapper();
                        wrapper_.where("task_id={0}", t.getId()).andNew("assignee_real={0}", taskParam.getAssignee());
                        tRuTask.setStatus(TaskStatusEnum.AGREE.status);
                        tRuTaskService.update(tRuTask, wrapper_);

                        result = new Result(true, "任务已通过！");
                    }
                }else{
                    if(approveCountTotal - approveCountNow + approveCountAgree < approveCountNeed){
                        //------------任务完成-未通过------------
                        String assignee = task.getAssignee();
                        taskService.setAssignee(task.getId(),StringUtils.isBlank(assignee)?(taskParam.getAssignee()+"_N"):(assignee+","+taskParam.getAssignee()+"_N"));
                        deleteProcessInstance(task.getProcessInstanceId(), "refused");
                        if(AssignType.PERSON.code.equals(taskParam.getAssignType())){
                            TRuTask tRuTask = new TRuTask();
                            tRuTask.setStatus(TaskStatusEnum.SKIP.status);
                            EntityWrapper wrapper_ = new EntityWrapper();
                            wrapper_.where("task_id={0}", t.getId()).andNew("status={0}", TaskStatusEnum.OPEN.status);
                            tRuTaskService.update(tRuTask, wrapper_);

                            wrapper_ = new EntityWrapper();
                            wrapper_.where("task_id={0}", t.getId()).andNew("assignee_real={0}", taskParam.getAssignee());
                            tRuTask.setStatus(TaskStatusEnum.REFUSE.status);
                            tRuTaskService.update(tRuTask, wrapper_);
                        }
                        return new Result(true, "任务已拒绝！");
                    }else{
                        //------------任务继续------------
                        String assignee = task.getAssignee();
                        taskService.setAssignee(task.getId(),StringUtils.isBlank(assignee)?(taskParam.getAssignee()+"_Y"):(assignee+","+taskParam.getAssignee()+"_Y"));
                        return new Result(true, "办理成功！");
                    }
                }
            } else {
                if (taskParam.getPass() == TaskStatusEnum.COMPLETE_AGREE.status) {
                    //设置原生工作流表哪些审批了
                    taskService.setAssignee(t.getId(), taskParam.getAssignee() + "_Y");
                    taskService.complete(t.getId(), map);
                    TRuTask tRuTask = new TRuTask();
                    tRuTask.setStatus(1);
                    EntityWrapper truWrapper = new EntityWrapper();
                    truWrapper.where("task_id={0}", t.getId()).andNew("assignee_real={0}", taskParam.getAssignee());

                    tRuTaskService.update(tRuTask, truWrapper);
                    result.setSuccess(true);
                } else if (taskParam.getPass() == TaskStatusEnum.COMPLETE_REFUSE.status) {
                    //拒绝任务
                    taskService.setAssignee(task.getId(), taskParam.getAssignee() + "_N");
                    deleteProcessInstance(task.getProcessInstanceId(), "refused");

                    TRuTask tRuTask = new TRuTask();
                    tRuTask.setStatus(TaskStatusEnum.REFUSE.status);
                    EntityWrapper truWrapper = new EntityWrapper();
                    truWrapper.where("task_id={0}", t.getId()).andNew("assignee_real={0}", taskParam.getAssignee());
                    tRuTaskService.update(tRuTask, truWrapper);
                    result.setMsg("任务已经拒绝！");
                    result.setCode(Constant.SUCCESS);
                    result.setSuccess(true);

                    return result;
                }
            }

            repairNextTaskNode(t,execution);

            List<Task> resultList = taskService.createTaskQuery().processInstanceId(t.getProcessInstanceId()).list();
            if(CollectionUtils.isEmpty(resultList)){
                finishProcessInstance(t.getProcessInstanceId(), ProcessStatusEnum.FINISHED_Y.status);
            }else{
                //设置审批人处理逻辑
                if (!Boolean.valueOf(map.get("customApprover").toString())) {
                    for (Task task1 : resultList) {
                        EntityWrapper tuserWrapper = new EntityWrapper();
                        tuserWrapper.where("proc_def_key={0}", processDefinition.getKey()).andNew("task_def_key={0}", task1.getTaskDefinitionKey()).andNew("version_={0}", processDefinition.getVersion());
                        //查询当前任务任务节点信息
                        TUserTask tUserTask1 = tUserTaskService.selectOne(tuserWrapper);
                        boolean flag = setAssignee(task1, tUserTask1);
                        if(!flag){
                            throw new WorkFlowException("设置审批人异常");
                        }
                    }
                }
            }

            result.setObj(setButtons(TaskNodeResult.toTaskNodeResultList(resultList)));
            result.setSuccess(true);
            result.setMsg("任务已办理成功");
            return result;
        }
    }

    /**
     * 处理由于跳转产生的异常节点任务，分两步处理：1添加缺失的节点；2删除多余节点；
     *
     * @author houjinrong@chtwm.com
     * date 2018/5/3 16:49
     */
    public void repairNextTaskNode(Task t,Execution execution) {
        EntityWrapper ew = new EntityWrapper();
        ew.where("status={0}", -2).andNew("proc_inst_id={0}", t.getProcessInstanceId());
        TRuTask tRuTask = tRuTaskService.selectOne(ew);
        String notDelete = "";
        List<String> talist= getNextTaskDefinitionKeys(t,false);

        if(talist.size()==1){
            String s=talist.get(0);
            List <Task> tasks=taskService.createTaskQuery().processInstanceId(t.getProcessInstanceId()).taskDefinitionKey(s).orderByTaskCreateTime().desc().list();
            //
            List<String> list=findBeforeTask(s,t.getProcessInstanceId(),t.getProcessDefinitionId(),true);
            boolean isCreate=true;
            if(list!=null&&list.size()>0){
                for(String key:list){
                    Task task=taskService.createTaskQuery().taskDefinitionKey(key).processInstanceId(t.getProcessInstanceId()).singleResult();
                    if(task!=null){
                        isCreate=false;
                        break;
                    }
                }
            }
            if((tasks==null||tasks.size()==0)&&isCreate) {
                long count = historyService.createHistoricActivityInstanceQuery().processInstanceId(t.getProcessInstanceId()).activityId(s).finished().count();
                if (count >= 1) {
                    managementService.executeCommand(new CreateCmd(t.getExecutionId(), s));
                    notDelete += s;
                }
            }
        }

        //第二步 删除多余节点
        List <Execution> exlist=null;
        if(execution==null||StringUtils.isBlank(execution.getParentId())){

        }else{
            exlist = runtimeService.createExecutionQuery().parentId(execution.getParentId()).list();
        }

        //处理删除由于跳转/拿回产生冗余的数据
        if (CollectionUtils.isNotEmpty(exlist)) {

            if (tRuTask != null) {
                HistoricTaskInstance taskInstance = historyService.createHistoricTaskInstanceQuery().taskId(tRuTask.getTaskId()).singleResult();
                List<HistoricTaskInstance> list = historyService.createHistoricTaskInstanceQuery().executionId(taskInstance.getExecutionId()).orderByTaskCreateTime().asc().list();
                notDelete +=","+ list.get(0).getTaskDefinitionKey();

                //查询审批的那条任务之后生成的任务
                Task task=taskService.createTaskQuery().taskDefinitionKey(list.get(0).getTaskDefinitionKey()).processInstanceId(t.getProcessInstanceId()).active().singleResult();

                for (int i = 0; i < exlist.size(); i++) {
                    //查询当前任务中每个任务信息
                    Task tas = taskService.createTaskQuery().executionId(exlist.get(i).getId()).singleResult();
                    if(tas==null||task==null||tas.getExecutionId().equals(task.getExecutionId())){
                        continue;
                    }
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String olde = sdf.format(tas.getCreateTime());

                    String newDate = (task == null ? "" : sdf.format(task.getCreateTime()));
                    if (notDelete.contains(tas.getTaskDefinitionKey()) ){
                        continue;
                    }else if(tRuTask != null && olde.equals(newDate)) {
                        TaskEntity entity = (TaskEntity) taskService.createTaskQuery().taskId(tas.getId()).singleResult();

                        entity.setExecutionId(null);
                        taskService.saveTask(entity);
                        taskService.deleteTask(entity.getId(), true);

                        EntityWrapper ewe = new EntityWrapper();
                        ewe.where("task_id={0}", tRuTask.getTaskId()).andNew("status={0}", -2);
                        tRuTaskService.delete(ewe);
                    }
                }
            }
        }
    }

    /**
     * 校验业务主键是否已经生成过任务
     *
     * @param processDefiniKey
     * @param bussinessKey
     * @param appKey
     * @return
     */
    protected Boolean checkBusinessKeyIsInFlow(String processDefiniKey, String bussinessKey, Integer appKey) {
        TaskQuery taskQuery = taskService.createTaskQuery().processDefinitionKey(processDefiniKey).processInstanceBusinessKey(bussinessKey);
        taskQuery.processVariableValueEquals("appKey", appKey);
        Task task = taskQuery.singleResult();

        if (task != null) {
            return true;
        }
        return false;
    }

    /**
     * 任务认领 部门，角色，组审批时，需具体人员认领任务
     * 认领是需要将认领人放置到t_ru_task表的assignee_real字段
     *
     * @param userId 认领人ID
     * @param taskId 任务ID
     * @param workId 节点任务具体执行ID，一个任务taskId对应多个审批人，每个审批人对应一个执行ID
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/23 14:55
     */
    @Override
    public Result taskClaim(String userId, String taskId, String workId) {
        TRuTask tRuTask = tRuTaskService.selectById(workId);
        if (tRuTask == null || !StringUtils.equals(taskId, tRuTask.getTaskId())) {
            return new Result(false, ResultEnum.TASK_NOT_EXIST.code, ResultEnum.TASK_NOT_EXIST.msg);
        }
        String assignee = tRuTask.getAssigneeReal();
        if (StringUtils.isNotBlank(assignee)) {
            assignee = assignee + "," + userId;
        } else {
            assignee = userId;
        }
        tRuTask = new TRuTask();
        tRuTask.setId(workId);
        tRuTask.setAssigneeReal(assignee);
        boolean updateFlag = tRuTaskService.updateById(tRuTask);
        if (updateFlag) {
            return new Result(true, ResultEnum.SUCCESS.code, ResultEnum.SUCCESS.msg);
        } else {
            return new Result(false, ResultEnum.FAIL.code, ResultEnum.FAIL.msg);
        }
    }

    /**
     * 取消任务认领
     *
     * @param userId 认领人ID
     * @param taskId 任务ID
     * @param workId 节点任务具体执行ID，一个任务taskId对应多个审批人，每个审批人对应一个执行ID
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/23 14:55
     */
    @Override
    public Result taskUnclaim(String userId, String taskId, String workId) {
        TRuTask tRuTask = tRuTaskService.selectById(workId);
        if (tRuTask == null || !StringUtils.equals(taskId, tRuTask.getTaskId())) {
            return new Result(false, ResultEnum.TASK_NOT_EXIST.code, ResultEnum.TASK_NOT_EXIST.msg);
        }
        String assignee = tRuTask.getAssigneeReal();
        if (StringUtils.isBlank(assignee)) {
            return new Result(false, ResultEnum.TASK_NOT_EXIST.code, ResultEnum.TASK_NOT_EXIST.msg);
        } else if (StringUtils.contains(assignee, userId)) {
            List<String> list = Arrays.asList(StringUtils.split(assignee,","));
            List<String> arraylist = Lists.newArrayList(list);
            arraylist.remove(userId);
            assignee = Joiner.on(",").join(arraylist);
        } else {
            return new Result(false, ResultEnum.ILLEGAL_REQUEST.code, ResultEnum.ILLEGAL_REQUEST.msg);
        }
        tRuTask = new TRuTask();
        tRuTask.setId(workId);
        tRuTask.setAssigneeReal(assignee);
        boolean updateFlag = tRuTaskService.updateById(tRuTask);
        if (updateFlag) {
            return new Result(true, ResultEnum.SUCCESS.code, ResultEnum.SUCCESS.msg);
        } else {
            return new Result(false, ResultEnum.FAIL.code, ResultEnum.FAIL.msg);
        }
    }

    /**
     * todo 初始化任务属性值
     * 跳转 管理员权限不受限制，可以任意跳转到已完成任务节点
     *
     * @param userId           操作人ID
     * @param taskId           任务ID
     * @param targetTaskDefKey 跳转到的任务节点KEY
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/18 16:00
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result taskJump(String userId, String taskId, String targetTaskDefKey) {
        //根据要跳转的任务ID获取其任务
        HistoricTaskInstance hisTask = historyService
                .createHistoricTaskInstanceQuery().taskId(taskId)
                .singleResult();
        //进而获取流程实例
        ProcessInstance instance = runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(hisTask.getProcessInstanceId())
                .singleResult();
        //取得流程定义
        ProcessDefinitionEntity definition = (ProcessDefinitionEntity) repositoryService.getProcessDefinition(hisTask.getProcessDefinitionId());
        //获取历史任务的Activity
        ActivityImpl hisActivity = definition.findActivity(targetTaskDefKey);
        //实现跳转
        ExecutionEntity e = managementService.executeCommand(new JumpCmd(hisTask.getExecutionId(), hisActivity.getId()));

        TRuTask tRuTask=new TRuTask();
        EntityWrapper en=new EntityWrapper();
        en.where("status={0}",-2).andNew("proc_inst_id={0}",instance.getId());
        tRuTaskService.delete(en);

        tRuTask.setStatus(-2);
        EntityWrapper entityWrapper1=new EntityWrapper();
        entityWrapper1.where("task_id={0}",taskId);
        tRuTaskService.update(tRuTask,entityWrapper1);

        boolean customApprover = (boolean) runtimeService.getVariable(instance.getProcessInstanceId(), "customApprover");

        if (!customApprover) {
            List<TaskEntity> tasks = e.getTasks();
            //设置审批人
            log.info("工作流平台设置审批人");
            for (int i = 0; i < tasks.size(); i++) {
                Task task = tasks.get(i);
                if (task.getTaskDefinitionKey().equals(targetTaskDefKey)) {
                    taskId += task.getId();
                    EntityWrapper entityWrapper = new EntityWrapper();
                    entityWrapper.where("proc_def_key={0}", definition.getKey()).andNew("task_def_key={0}", task.getTaskDefinitionKey()).andNew("version_={0}", definition.getVersion());
                    //查询当前任务任务节点信息
                    TUserTask tUserTask = tUserTaskService.selectOne(entityWrapper);
                    boolean flag = setAssignee(task, tUserTask);
                }
            }
        }
        return new Result();
    }

    /**
     * todo 用户组权限判断
     * 转办 管理员权限不受限制，可以任意设置转办
     *
     * @param userId       操作人ID
     * @param taskId       任务ID
     * @param targetUserId 转办人ID
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/18 16:00
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result taskTransfer(String userId, String taskId, String targetUserId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return new Result(ResultEnum.TASK_NOT_EXIST.code, ResultEnum.TASK_NOT_EXIST.msg);
        }

        EntityWrapper<TRuTask> wrapper = new EntityWrapper();
        wrapper.where("task_id={0}", taskId);

        String oldUser = userId;

        if(userId.indexOf(":") > -1){
            String[] array = userId.split(":");
            userId = array[0];
            oldUser = array[1];
        }

        wrapper.and("assignee={0}", userId);
        TRuTask tRuTask = tRuTaskService.selectOne(wrapper);

        //用户组权限判断
        if (!ConstantUtils.ADMIN_ID.equals(userId) && tRuTask == null) {
            return new Result(false, "您所在的用户组没有权限进行该操作");
        }

        if(StringUtils.contains(tRuTask.getAssigneeReal(), targetUserId)){
            return new Result(false, "办理人已存在，同一办理人只能办理一次");
        }
        tRuTask.setAssigneeReal(tRuTask.getAssigneeReal().replace(oldUser, targetUserId));
        tRuTaskService.updateById(tRuTask);
        return new Result(true, "转办任务成功");
    }

    /**
     * 催办 只有申请人可以催办
     *
     * @param userId 操作人ID
     * @param taskId 任务 ID
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/18 16:00
     */
    @Override
    public Result taskRemind(String userId, String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return new Result(ResultEnum.TASK_NOT_EXIST.code, ResultEnum.TASK_NOT_EXIST.msg);
        }

        RemindTask remindTask = new RemindTask();
        remindTask.setReminderId(userId);
        remindTask.setProcInstId(task.getProcessInstanceId());
        remindTask.setTaskId(taskId);
        remindTask.setTaskName(task.getName());
        remindTask.setIsFinished(TaskStatusEnum.REMIND_UNFINISHED.status);

        boolean insertFlag = remindTaskService.insert(remindTask);
        if (insertFlag) {
            //发送邮件

            return new Result(true, ResultEnum.SUCCESS.code, ResultEnum.SUCCESS.msg);
        }
        return new Result(false, ResultEnum.FAIL.code, ResultEnum.FAIL.msg);
    }

    /**
     * 问询
     *
     * @param userId            操作人ID
     * @param processInstanceId 任务流程实例ID
     * @param currentTaskDefKey 当前问询任务节点KEY
     * @param targetTaskDefKey  目标问询任务节点KEY
     * @param commentResult     意见
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/18 16:01
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result taskEnquire(String userId, String processInstanceId, String currentTaskDefKey, String targetTaskDefKey, String commentResult) {
        Task task = taskService.createTaskQuery().processInstanceId(processInstanceId).taskDefinitionKey(currentTaskDefKey).singleResult();
        if (task == null) {
            log.error("问询的任务不存在 processInstanceId:{},taskDefinitionKey:{}", processInstanceId, currentTaskDefKey);
            return new Result(ResultEnum.TASK_NOT_EXIST.code, ResultEnum.TASK_NOT_EXIST.msg);
        }
        //todo


        //校验是否是上级节点
        List<String> parentNodes = getBeforeTaskDefinitionKeys(task, true);
        if (!parentNodes.contains(targetTaskDefKey)) {
            return new Result(false, "无权问询该节点");
        }

        //校验是否已有问询
        EntityWrapper<TAskTask> wrapper = new EntityWrapper<>();
        wrapper.where("proc_inst_id={0}", processInstanceId)
                .where("execution_id={0}", task.getExecutionId())
                .where("current_task_key={0}", currentTaskDefKey)
                .where("ask_task_key={0}", targetTaskDefKey)
                .where("is_ask_end=0");
        List<TAskTask> list = tAskTaskService.selectList(wrapper);
        if (CollectionUtils.isNotEmpty(list)) {
            return new Result(false, "已存在问询任务");
        }

        TAskTask askTask = new TAskTask();
        askTask.setProcInstId(task.getProcessInstanceId());
        askTask.setCurrentTaskId(task.getId());
        askTask.setCurrentTaskKey(task.getTaskDefinitionKey());
        askTask.setExecutionId(task.getExecutionId());
        askTask.setIsAskEnd(0);
        askTask.setAskTaskKey(targetTaskDefKey);
        askTask.setCreateTime(new Date());
        askTask.setUpdateTime(new Date());
        askTask.setCreateId(userId);
        askTask.setUpdateId(userId);
        askTask.setAskUserId(userId);
        askTask.setAskComment(commentResult);
        boolean success = tAskTaskService.insert(askTask);
        if (!success) {
            return new Result(false, "问询失败");
        }
        TWorkDetail tWorkDetail = new TWorkDetail();
        tWorkDetail.setTaskId(task.getId());
        tWorkDetail.setOperator(userId);
        tWorkDetail.setProcessInstanceId(task.getProcessInstanceId());
        tWorkDetail.setCreateTime(new Date());
        tWorkDetail.setDetail("工号【" + userId + "】问询了该任务，问询内容是【" + commentResult + "】");
        workDetailService.insert(tWorkDetail);
        return new Result(true, "问询成功");
    }

    /**
     * 问询确认
     *
     * @param userId        操作人ID
     * @param askId         问询
     * @param answerComment 确认信息
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/18 16:01
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result taskConfirmEnquire(String userId, String askId, String answerComment) {
        EntityWrapper<TAskTask> wrapper = new EntityWrapper<>();
        wrapper.where("`ask_user_id`={0}", userId)
                .where("id={0}", askId)
                .where("is_ask_end={0}", 0);
        TAskTask tAskTask = tAskTaskService.selectOne(wrapper);
        if (tAskTask == null) {
            log.error("问询不存在或状态为已确认 askId:{}", askId);
            return new Result(false, "问询确认失败");
        }
        List<HistoricTaskInstance> list = historyService.createHistoricTaskInstanceQuery().processInstanceId(tAskTask.getProcInstId()).taskDefinitionKey(tAskTask.getCurrentTaskKey()).list();
        if (CollectionUtils.isEmpty(list)) {
            log.error("确认问询的任务不存在 askId:{}", askId);
            return new Result(ResultEnum.TASK_NOT_EXIST.code, ResultEnum.TASK_NOT_EXIST.msg);
        }
        tAskTask.setUpdateTime(new Date());
        tAskTask.setAnswerComment(answerComment);
        tAskTask.setIsAskEnd(1);
        boolean success = tAskTaskService.updateById(tAskTask);
        if (!success) {
            return new Result(false, "问询确认失败");
        }
        TWorkDetail tWorkDetail = new TWorkDetail();
        tWorkDetail.setTaskId(tAskTask.getCurrentTaskId());
        tWorkDetail.setOperator(userId);
        tWorkDetail.setProcessInstanceId(tAskTask.getProcInstId());
        tWorkDetail.setCreateTime(new Date());
        tWorkDetail.setDetail("工号【" + userId + "】回复了该问询，回复内容是【" + answerComment + "】");
        workDetailService.insert(tWorkDetail);
        return new Result(true, "问询确认成功");
    }

    /**
     * 任务驳回
     *
     * @param userId 操作人ID
     * @param taskId 任务ID
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/18 16:01
     */
    @Override
    public Result taskRollback(String userId, String taskId) {
        List<String> taskDefKeysForRollback = getTaskDefKeysForRollback(taskId);
        if (CollectionUtils.isEmpty(taskDefKeysForRollback)) {
            return new Result(false, ResultEnum.TASK_ROLLBACK_FORBIDDEN.code, ResultEnum.TASK_ROLLBACK_FORBIDDEN.msg);
        }
        for (String taskDefKey : taskDefKeysForRollback) {
            taskJump(userId, taskId, taskDefKey);
        }
        return new Result(true, ResultEnum.SUCCESS.code, ResultEnum.SUCCESS.msg);
    }

    /**
     * 任务撤回
     *
     * @param userId        用户ID
     * @param taskId        任务ID
     * @param targetTaskKey 要撤回到的任务节点key
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/27 17:03
     */
    @Override
    public Result taskRevoke(String userId, String taskId, String targetTaskKey) {
        TaskEntity taskEntity = (TaskEntity) taskService.createTaskQuery().taskId(taskId).singleResult();
        if (!isAllowRollback(taskEntity)) {
            return new Result(false, ResultEnum.TASK_ROLLBACK_FORBIDDEN.code, ResultEnum.TASK_ROLLBACK_FORBIDDEN.msg);
        }
        return taskJump(userId, taskId, targetTaskKey);
    }

    /**
     * 取消 只有流程发起人方可进行取消操作
     *
     * @param userId            操作人ID
     * @param processInstanceId 流程实例ID
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/18 16:01
     */
    @Override
    public Result taskCancel(String userId, String processInstanceId) {
        HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        if (historicProcessInstance != null) {
            String startUserId = historicProcessInstance.getStartUserId();
            if (startUserId.equals(processInstanceId)) {
                deleteProcessInstance(processInstanceId, "");
            } else {
                return new Result(false, ResultEnum.PERMISSION_DENY.code, ResultEnum.PERMISSION_DENY.msg);
            }
        } else {
            return new Result(false, ResultEnum.PROCINST_NOT_EXIST.code, ResultEnum.PROCINST_NOT_EXIST.msg);
        }

        return new Result(true, ResultEnum.SUCCESS.code, ResultEnum.SUCCESS.msg);
    }

    /**
     * 挂起流程
     *
     * @param userId            操作人ID
     * @param processInstanceId 流程实例ID
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/18 16:03
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result processSuspend(String userId, String processInstanceId) {
        runtimeService.suspendProcessInstanceById(processInstanceId);
        TWorkDetail tWorkDetail = new TWorkDetail();
        tWorkDetail.setOperator(userId);
        tWorkDetail.setProcessInstanceId(processInstanceId);
        tWorkDetail.setCreateTime(new Date());
        tWorkDetail.setDetail("工号【" + userId + "】挂起了该流程");
        workDetailService.insert(tWorkDetail);
        return new Result(true, "挂起流程成功");
    }

    /**
     * 激活流程
     *
     * @param userId            操作人ID
     * @param processInstanceId 流程实例ID
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/18 16:03
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result processActivate(String userId, String processInstanceId) {
        runtimeService.activateProcessInstanceById(processInstanceId);
        TWorkDetail tWorkDetail = new TWorkDetail();
        tWorkDetail.setOperator(userId);
        tWorkDetail.setProcessInstanceId(processInstanceId);
        tWorkDetail.setCreateTime(new Date());
        tWorkDetail.setDetail("工号【" + userId + "】激活了该流程");
        workDetailService.insert(tWorkDetail);
        return new Result(true, "激活流程成功");
    }

    /**
     * 问询意见查询接口
     *
     * @param userId 操作人ID
     * @param askId  问询id
     * @return
     */
    @Override
    public Result askComment(String userId, String askId) {
        EntityWrapper<TAskTask> wrapper = new EntityWrapper<>();
        wrapper.where("id={0}", askId);
        TAskTask askTask = tAskTaskService.selectOne(wrapper);
        if (askTask == null) {
            return new Result(false, "问询不存在");
        }
        HistoricTaskInstance task = historyService.createHistoricTaskInstanceQuery().processInstanceId(askTask.getProcInstId()).taskDefinitionKey(askTask.getAskTaskKey()).singleResult();
        if (task == null) {
            return new Result(ResultEnum.TASK_NOT_EXIST.code, ResultEnum.TASK_NOT_EXIST.msg);
        }
        Result result = new Result(true, "查询成功");
        AskCommentDetailVo detailVo = new AskCommentDetailVo();
        detailVo.setAskComment(askTask.getAskComment());
        detailVo.setAnswerComment(askTask.getAnswerComment());
        detailVo.setProcInstId(askTask.getProcInstId());
        detailVo.setCurrentTaskKey(askTask.getCurrentTaskKey());
        detailVo.setAskTaskKey(askTask.getAskTaskKey());
        result.setObj(detailVo);
        return result;
    }

    /**
     * 未办任务列表
     *
     * @return 分页
     * @author houjinrong@chtwm.com
     * date 2018/4/20 15:35
     */
    @Override
    public void openTaskList(PageInfo pageInfo) {
        Page<TaskResult> page = new Page<TaskResult>(pageInfo.getNowpage(), pageInfo.getSize());
        List<TaskResult> list = workflowDao.queryOpenTask(page, pageInfo.getCondition());
        pageInfo.setRows(list);
        pageInfo.setTotal(page.getTotal());
    }

    /**
     * 已办任务列表
     *
     * @return json
     * @author houjinrong@chtwm.com
     * date 2018/4/19 15:17
     */
    @Override
    public void closeTaskList(PageInfo pageInfo) {
        Page<TaskResult> page = new Page<TaskResult>(pageInfo.getNowpage(), pageInfo.getSize());
        List<TaskResult> list = workflowDao.queryCloseTask(page, pageInfo.getCondition());
        for(TaskResult t : list){
            t.setAssigneeNext(getNextAssignee(t.getTaskId()));
        }
        pageInfo.setRows(list);
        pageInfo.setTotal(page.getTotal());
    }

    /**
     * 待处理任务（包括待认领和待办任务）
     *
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/23 16:01
     */
    @Override
    public void activeTaskList(PageInfo pageInfo) {
        Page<TaskResult> page = new Page<TaskResult>(pageInfo.getNowpage(), pageInfo.getSize());
        List<TaskResult> list = workflowDao.queryActiveTask(page, pageInfo.getCondition());
        for(TaskResult t : list){
            t.setAssigneeBefore(getBeforeAssignee(t.getTaskId()));
        }
        pageInfo.setRows(list);
        pageInfo.setTotal(page.getTotal());
    }

    /**
     * 待认领任务列表， 任务签收后变为待办任务，待办任务可取消签认领
     *
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/23 15:59
     */
    @Override
    public void claimTaskList(PageInfo pageInfo) {
        Page<TaskResult> page = new Page<TaskResult>(pageInfo.getNowpage(), pageInfo.getSize());
        List<TaskResult> list = workflowDao.queryClaimTask(page, pageInfo.getCondition());
        pageInfo.setRows(list);
        pageInfo.setTotal(page.getTotal());
    }

    /**
     * 任务相关列表查询
     *
     * @param taskQueryParam
     * @param type
     * @return
     */
    @Override
    public PageInfo myTaskPage(TaskQueryParam taskQueryParam, String type) {
        StringBuffer sb = new StringBuffer();
        StringBuffer con = new StringBuffer();
        con.append(" WHERE 1=1 ");
        String re;
        String reC = "SELECT COUNT(*)";

        String assignee = taskQueryParam.getAssignee();
        String roleId = null;

        List<RbacRole> roleList = privilegeService.getAllRoleByUserId(taskQueryParam.getAppKey(), assignee);

        if(CollectionUtils.isNotEmpty(roleList)) {
            roleId = StringUtils.join(roleList, ",");
        }

        if (TaskListEnum.CLOSE.type.equals(type)) {
            re = "SELECT ahp.START_USER_ID_ AS OWNER_,ahp.NAME_ AS CATEGORY_,ahp.BUSINESS_KEY_ AS DESCRIPTION_,art.* ";
            sb.append(" FROM act_hi_taskinst AS art ");
        } else {
            re = "SELECT trt.assignee_real AS ASSIGNEE_,ahp.START_USER_ID_ AS OWNER_,trt.STATUS AS PRIORITY_,ahp.NAME_ AS CATEGORY_,ahp.BUSINESS_KEY_ AS DESCRIPTION_,art.* ";
            sb.append(" FROM t_ru_task AS trt LEFT JOIN act_ru_task AS art ON trt.TASK_ID=art.ID_ ");
        }

        sb.append(" LEFT JOIN act_hi_procinst AS ahp ON art.PROC_INST_ID_=ahp.PROC_INST_ID_ ");

        if (taskQueryParam.getAppKey() != null || StringUtils.isNotBlank(taskQueryParam.getCreatorDept())) {
            sb.append(" LEFT JOIN t_ru_procinst AS tap ON art.PROC_INST_ID_=tap.PROC_INST_ID ");
            if(taskQueryParam.getAppKey() != null){
                con.append(" AND tap.APP_KEY = #{appKey}");
            }
            if(StringUtils.isNotBlank(taskQueryParam.getCreatorDept())){
                con.append(" AND tap.creator_dept = #{creatorDept} ");
            }
        }

        if(StringUtils.isNotBlank(taskQueryParam.getProcInstId())){
            con.append(" AND art.PROC_INST_ID_ = #{procInstId} ");
        }

        if (StringUtils.isNotBlank(taskQueryParam.getTitle())) {
            con.append(" AND ahp.NAME_ LIKE #{title} ");
        }

        if (StringUtils.isNotBlank(taskQueryParam.getCreator())) {
            con.append(" AND ahp.START_USER_ID_ = #{creator} ");
        }

        if (StringUtils.isNotBlank(taskQueryParam.getBusinessKey())) {
            con.append(" AND ahp.BUSINESS_KEY_ = #{businessKey} ");
        }

        if (StringUtils.isNotBlank(taskQueryParam.getTaskName())) {
            con.append(" AND art.NAME_ LIKE #{taskName} ");
        }

        if(StringUtils.isNotBlank(taskQueryParam.getTaskId())){
            con.append(" AND art.ID_ = #{taskId} ");
        }

        if(StringUtils.isNotBlank(taskQueryParam.getCreateTimeStart())){
            con.append(" AND art.CREATE_TIME_ >= #{createTimeStart} ");
        }

        if(StringUtils.isNotBlank(taskQueryParam.getCreateTimeEnd())){
            con.append(" AND art.CREATE_TIME_ <= #{createTimeEnd} ");
        }

        if (TaskListEnum.CLOSE.type.equals(type)) {
            if(ProcessStatusEnum.UNFINISHED.status == taskQueryParam.getProcInstState()){
                con.append(" ahp.DELETE_REASON_ IS NULL ");
            }else if(ProcessStatusEnum.UNFINISHED.status == taskQueryParam.getProcInstState()){
                con.append(" ahp.DELETE_REASON_ IS NOT NULL ");
            }
            con.append(" AND art.ASSIGNEE_ LIKE #{assignee} ");
            assignee = assignee + "_";
        } else if (TaskListEnum.CLAIM.type.equals(type)) {
            con.append(" AND art.SUSPENSION_STATE_=1 ");
            con.append(" AND trt.STATUS = " + TaskStatusEnum.BEFORESIGN.status);
            con.append(" AND (");
            con.append(" (trt.ASSIGNEE_TYPE =" + AssignType.PERSON.code + " AND trt.ASSIGNEE = #{assignee}) ");

            if(StringUtils.isNotBlank(roleId)){
                con.append(" OR (trt.ASSIGNEE_TYPE =" + AssignType.ROLE.code + " AND trt.ASSIGNEE IN (#{roleId}) AND #{assignee} NOT IN (trt.ASSIGNEE_REAL)) ");
            }
            con.append(")");
        } else if (TaskListEnum.ACTIVE.type.equals(type)) {
            con.append(" AND art.SUSPENSION_STATE_=1 ");
            con.append(" AND trt.STATUS IN (" + TaskStatusEnum.BEFORESIGN.status + "," + TaskStatusEnum.OPEN.status + ") ");
            con.append(" AND (");
            con.append(" (trt.ASSIGNEE_TYPE =" + AssignType.PERSON.code + " AND trt.ASSIGNEE = #{assignee}) ");

            if(StringUtils.isNotBlank(roleId)){
                con.append(" OR (trt.ASSIGNEE_TYPE =" + AssignType.ROLE.code + " AND trt.ASSIGNEE IN (#{roleId})) ");
            }
            con.append(")");
        } else {
            con.append(" AND art.SUSPENSION_STATE_=1 ");
            con.append(" AND trt.STATUS=" + TaskStatusEnum.OPEN.status);
            con.append(" AND trt.ASSIGNEE_REAL LIKE #{assignee} ");
        }
        PageInfo pageInfo = new PageInfo(taskQueryParam.getPage(), taskQueryParam.getRows());
        String sql = sb.toString() + con.toString();
        if (TaskListEnum.CLOSE.type.equals(type)) {
            NativeHistoricTaskInstanceQuery query = historyService.createNativeHistoricTaskInstanceQuery()
                    .parameter("appKey", taskQueryParam.getAppKey())
                    .parameter("title", "%" + taskQueryParam.getTitle() + "%")
                    .parameter("creator", taskQueryParam.getCreator())
                    .parameter("taskName", "%" + taskQueryParam.getTaskName() + "%")
                    .parameter("assignee", "%" + assignee + "%")
                    //.parameter("departmentId", departmentId)
                    .parameter("roleId", roleId);
            List<HistoricTaskInstance> tasks = query.sql(re + sql).listPage(pageInfo.getFrom(), pageInfo.getSize());
            pageInfo.setTotal((int) query.sql(reC + sql).count());
            pageInfo.setRows(transferHisTask(taskQueryParam.getAssignee(), tasks,false));
        } else {
            NativeTaskQuery query = taskService.createNativeTaskQuery()
                    .parameter("appKey", taskQueryParam.getAppKey())
                    .parameter("title", "%" + taskQueryParam.getTitle() + "%")
                    .parameter("creator", taskQueryParam.getCreator())
                    .parameter("taskName", "%" + taskQueryParam.getTaskName() + "%")
                    .parameter("assignee", assignee)
                    //.parameter("departmentId", departmentId)
                    .parameter("roleId", roleId);
            List<Task> tasks = query.sql(re + sql).listPage(pageInfo.getFrom(), pageInfo.getSize());
            pageInfo.setRows(tasks);
            pageInfo.setTotal((int) query.sql(reC + sql).count());
            pageInfo.setRows(transferTask(taskQueryParam.getAssignee(), tasks,false));
        }

        return pageInfo;
    }

    /**
     * 任务相关列表查询
     *
     * @param taskQueryParam
     * @param type
     * @return
     */
    @Override
    public PageInfo allTaskPage(TaskQueryParam taskQueryParam, String type) {
        StringBuffer sb = new StringBuffer();
        StringBuffer con = new StringBuffer();
        con.append(" WHERE 1=1 ");
        String re;
        String reC = "SELECT COUNT(*)";
        String groupBy = " GROUP BY art.ID_ ";

        if (TaskListEnum.CLOSE.type.equals(type)) {
            re = "SELECT GROUP_CONCAT(art.ASSIGNEE_) AS ASSIGNEE_,ahp.START_USER_ID_ AS OWNER_,ahp.NAME_ AS CATEGORY_,ahp.BUSINESS_KEY_ AS DESCRIPTION_,art.* ";
            sb.append(" FROM act_hi_taskinst AS art LEFT JOIN t_ru_task AS trt ON trt.TASK_ID=art.ID_ ");
        } else {
            re = "SELECT GROUP_CONCAT(trt.assignee_real) AS ASSIGNEE_,GROUP_CONCAT(ahp.START_USER_ID_) AS OWNER_,GROUP_CONCAT(trt.STATUS) AS PRIORITY_,GROUP_CONCAT(ahp.NAME_) AS CATEGORY_,GROUP_CONCAT(ahp.BUSINESS_KEY_) AS DESCRIPTION_,art.* ";
            sb.append(" FROM act_ru_task AS art LEFT JOIN t_ru_task AS trt ON trt.TASK_ID=art.ID_ ");
        }

        if (taskQueryParam.getAppKey() != null) {
            sb.append(" LEFT JOIN t_ru_procinst AS tap ON art.PROC_INST_ID_=tap.PROC_INST_ID ");
            con.append(" AND tap.APP_KEY = #{appKey}");
        }

        sb.append(" LEFT JOIN act_hi_procinst AS ahp ON art.PROC_INST_ID_=ahp.PROC_INST_ID_ ");
        if (StringUtils.isNotBlank(taskQueryParam.getTitle())) {
            con.append(" AND ahp.NAME_ LIKE #{title} ");
        }

        if (StringUtils.isNotBlank(taskQueryParam.getCreator())) {
            con.append(" AND ahp.START_USER_ID_ = #{creator} ");
        }

        if (StringUtils.isNotBlank(taskQueryParam.getBusinessKey())) {
            con.append(" AND ahp.BUSINESS_KEY_ = #{businessKey} ");
        }

        if (StringUtils.isNotBlank(taskQueryParam.getTaskName())) {
            con.append(" AND art.NAME_ LIKE #{taskName} ");
        }

        if (TaskListEnum.CLOSE.type.equals(type)) {
            con.append(" AND art.DELETE_REASON_ IN ('completed','refused') ");
        } else if (TaskListEnum.CLAIM.type.equals(type)) {
            con.append(" AND art.SUSPENSION_STATE_=1 ");
            con.append(" AND trt.STATUS = " + TaskStatusEnum.BEFORESIGN.status);
        } else if (TaskListEnum.ACTIVE.type.equals(type)) {
            con.append(" AND art.SUSPENSION_STATE_=1 ");
            con.append(" AND trt.STATUS IN (" + TaskStatusEnum.BEFORESIGN.status + "," + TaskStatusEnum.OPEN.status + ") ");
        } else {
            con.append(" AND art.SUSPENSION_STATE_=1 ");
            con.append(" AND trt.STATUS=" + TaskStatusEnum.OPEN.status);
        }

        PageInfo pageInfo = new PageInfo(taskQueryParam.getPage(), taskQueryParam.getRows());
        String sql = sb.toString() + con.toString();
        if (TaskListEnum.CLOSE.type.equals(type)) {
            NativeHistoricTaskInstanceQuery query = historyService.createNativeHistoricTaskInstanceQuery()
                    .parameter("appKey", taskQueryParam.getAppKey())
                    .parameter("title", "%" + taskQueryParam.getTitle() + "%")
                    .parameter("creator", taskQueryParam.getCreator())
                    .parameter("taskName", "%" + taskQueryParam.getTaskName() + "%")
                    .parameter("businessKey", taskQueryParam.getBusinessKey());

            String dataSql = re + sql + groupBy;
            String countSql = reC + " FROM (" + (reC + sql + groupBy) + ") AS temp";
            List<HistoricTaskInstance> tasks = query.sql(dataSql).listPage(pageInfo.getFrom(), pageInfo.getSize());
            pageInfo.setTotal((int) query.sql(countSql).count());
            pageInfo.setRows(transferHisTask(taskQueryParam.getAssignee(), tasks, true));
        } else {
            NativeTaskQuery query = taskService.createNativeTaskQuery()
                    .parameter("appKey", taskQueryParam.getAppKey())
                    .parameter("title", "%" + taskQueryParam.getTitle() + "%")
                    .parameter("creator", taskQueryParam.getCreator())
                    .parameter("taskName", "%" + taskQueryParam.getTaskName() + "%")
                    .parameter("businessKey", taskQueryParam.getBusinessKey());

            String dataSql = re + sql + groupBy;
            String countSql = reC + " FROM (" + (reC + sql + groupBy) + ") AS temp";
            List<Task> tasks = query.sql(dataSql).listPage(pageInfo.getFrom(), pageInfo.getSize());
            pageInfo.setRows(tasks);
            pageInfo.setTotal((int) query.sql(countSql).count());
            pageInfo.setRows(transferTask(taskQueryParam.getAssignee(), tasks,true));
        }

        return pageInfo;
    }

    /**
     * 获取父级任务节点
     *
     * @param taskId 当前任务节点id
     * @param isAll  是否递归获取全部父节点
     * @return
     */
    @Override
    public Result getParentNodes(String taskId, String userId, boolean isAll) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            log.warn("任务不存在 taskId {}", taskId);
            return new Result(ResultEnum.TASK_NOT_EXIST.code, ResultEnum.TASK_NOT_EXIST.msg);
        }

        try {
            Map<String, FlowNode> beforeTask = findBeforeTask(taskId, true);
            Iterator<Map.Entry<String, FlowNode>> iterator = beforeTask.entrySet().iterator();
            List<TaskVo> taskList = Lists.newArrayList();
            while(iterator.hasNext()){
                Map.Entry<String, FlowNode> next = iterator.next();
                FlowNode node = next.getValue();
                TaskVo taskVo = new TaskVo();
                taskVo.setTaskDefinitionKey(node.getId());
                taskVo.setTaskName(node.getName());

                taskList.add(taskVo);
            }
            Result result = new Result(true, "查询成功");
            result.setObj(taskList);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Result(ResultEnum.TASK_NOT_EXIST.code, ResultEnum.TASK_NOT_EXIST.msg);
    }

    /**
     * 我的流程
     *
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/23 15:59
     */
    @Override
    public void processInstanceList(PageInfo pageInfo){
        Page<ProcessInstanceResult> page = new Page<ProcessInstanceResult>(pageInfo.getNowpage(), pageInfo.getSize());
        List<ProcessInstanceResult> list = workflowDao.queryProcessInstance(page, pageInfo.getCondition());
        pageInfo.setRows(list);
        pageInfo.setTotal(page.getTotal());
    }

    /**
     * 待处理任务总数（包括待认领和待办任务）
     *
     * @return
     * @author houjinrong@chtwm.com
     * date 2018/4/23 16:01
     */
    @Override
    public Long activeTaskCount(Map<String,Object> paraMap){
        return workflowDao.activeTaskCount(paraMap);
    }
}