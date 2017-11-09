package com.activiti.service;

import com.activiti.entity.CommonVo;
import com.activiti.expection.WorkFlowException;
import com.github.pagehelper.PageInfo;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricProcessInstanceQuery;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.task.Comment;
import org.activiti.engine.task.Task;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Created by ma on 2017/7/18.
 */
public interface WorkTaskService {
    /**
     * 开启任务
     * @param commonVo
     * @param paramMap   流程定义中线上的参数，键是线上的键
     * @return  返回部署的任务id
     */
    String startTask(CommonVo commonVo,Map<String,Object> paramMap);
    /**
     * 通过用户相关信息查询待审批任务
     * @param userId  用户信息 一般是id
     * @param  startPage  起始页数
     * @param  pageSize   每页显示数
     * @param  bussnessType  业务系统的key
     * @return  返回任务列表
     */
    PageInfo<Task> queryByAssign(String userId, int startPage, int pageSize,String bussnessType);



    /**
     * 通过用户id查询用户历史任务信息
     * @param userId      用户主键
     * @param startPage  起始页数
     * @param pageSize  每页显示数
     * @param  type      查询历史任务类型
     *                   0：未完成的历史任务
     *                   1：已完成的历史任务
     *                   -1：全部历史任务
     * @param bussnessKey  业务系统key
     * @return 历史审批任务信息列表
     */
    List<HistoricTaskInstance> queryHistoryList(String userId, int startPage, int pageSize,String bussnessKey,int type);

    /**
     * 审批接口
     *         注：当下一个审批人的唯一标识为空或不传时，直接完成该任务
     * @param processId  proc_inst_id值
     * @param currentUser  当前审批人信息
     * @param commentResult 审批类型
     *                     2  审批通过
     *                     3 审批拒绝
     * @param commentContent    审批意见
     * @return   返回 true  or false
     * @exception  WorkFlowException 返回审批异常
     */
    String  completeTask(String processId,String currentUser ,String commentContent, String commentResult) throws WorkFlowException;

    /**
     * 回退到上一节点
     * @param taskId  任务id
     * @param note    审批意见
     * @return  返回成功或失败
     *          true:成功
     *          false:失败
     */
    //boolean rollBack(String taskId,String note);

    /**
     * 审批不通过
     * @param processId  流程任务中的processId
     * @param reason     拒绝理由
     * @return           返回成功或失败
     *                      true:成功
     *                      false:失败
     */
    Boolean refuseTask(String processId,String reason) throws WorkFlowException;
    /**
     * 获取申请人提交的任务
     * @param userid  申请人信息
     * @param startPage  起始页数
     * @param pageSzie    查询多少条数
     * @param status      0 :审批中的任务
     *                    1 ：审批完成的任务
     *
     * @return    返回申请人提交的任务
     */
    List<HistoricProcessInstance> getApplyTasks(String userid, int startPage, int pageSzie, int status);
    /**
     * 获取参与审批用户的审批历史任务
     * @param userid   审批人用户唯一标识
     * @param startPage   起始页数
     * @param pageSzie     查询多少条数

     *
     * @return    返回参与用户的审批历史信息
     */
    List<HistoricProcessInstance> getInvolvedUserCompleteTasks(String userid,int startPage,int pageSzie);

    /**
     * 通过用户主键查询历史审批过的任务
     * @param userId   用户主键
     * @param startPage   开始页数
     * @param pagegSize   每页显示数
     * @return            返回审批历史人物信息列表
     */
    PageInfo<HistoricTaskInstance> selectMyComplete(String userId,int startPage,int pagegSize);

    /**
     * 通过用户主键查询审批拒绝的信息
     * @param userId   用户主键
     * @param startPage 开始页数
     * @param pageSize   结束页数
     * @return            返回用户拒绝的信息
     */
    PageInfo<HistoricTaskInstance> selectMyRefuse(String userId,int startPage,int pageSize);
    /**
     *查询任务当所在节点
     * @param processId  流程定义id
     * @return  返回图片流 ，二进制
     */
/*    byte[] generateImage(String processId);*/

    /**
     * 查询业务主键是否再流程钟
     * @param bussinessKey   业务主键
     * @return   返回true or false
     */
    boolean checekBunessKeyIsInFlow(String bussinessKey);

    /**
     * 获取当前历史任务的审批意见
     * @param taskId   任务id
     * @return  返回审批意见
     */
    Comment selectComment(String taskId);

    /**
     * 通过流程实例id查询任务审批历史信息
     * @param processId   流程任务中processId
     * @return  返回历史审批信息列表
     */
    List<HistoricTaskInstance> selectTaskHistory(String processId);
    /**
     * 获取任务审批意见列表
     * @param processInstanceId   流程任务中的processId
     * @return   返回审批意见列表
     */
    List<Comment> selectListComment(String processInstanceId);

    /**
     * 通过历史任务id查询历史任务
     * @param taskHistoryId 任务历史id
     * @return   返回历史任务信息
     */
    HistoricTaskInstance selectHistoryTask(String taskHistoryId);

    /**
     * 通过流程定义id获取定义变量
     * @param processId  流程定义id
     * @return  返回自定义变量map
     */
    Map<String, Object> getVariables(String processId);

    /**
     * 通过流程定义id查询下一流程
     * @param procInstanceId 流程任务中的processId
     * @return  返回下一节点名称
     */
/*    String getNextNode(String procInstanceId);*/

    /**
     * 查询所有待审批的任务
     * @param startPage  开始页
     * @param pageSize    每页显示数
     * @return   分页显示审批任务列表
     */
    PageInfo<Task> selectAllWaitApprove(int startPage,int pageSize);

    /**
     * 查询所有通过的任务
     * @param startPage  开始页
     * @param pageSize   每页显示数
     * @return  分页显示审批通过任务列表
     */
    PageInfo<HistoricProcessInstance> selectAllPassApprove(int startPage, int pageSize);

    /**
     * 查询所有拒绝的任务
     * @param startPage  开始页
     * @param pageSize   每页显示数
     * @return  分页显示所有拒绝的任务列表
     */
    PageInfo<HistoricProcessInstance> selectAllRefuseApprove(int startPage,int pageSize);

    /**
     * 通过流程定义id判断活动是否通过
     * @param processId   流程定义id
     * @return   true:通过；false:拒绝
     */
    boolean checkIsPass(String processId);

/*    *//**
     * 获取最后审批人
     * @param processId  流程中processId
     * @return  返回最后审批人userCode
     *//*
    String getLastApprover(String processId);

    *//**
     * 加入会签
     * @param taskId  任务id
     * @param list   人员userCode列表
     *//*
    void jointProcess(String taskId,List<String> list);*/

    /**
     * 通过流程定义id查询任务
     * @param processId   流程定义id
     * @return  返回任务
     */
    Task queryTaskByProcessId(String processId);

    /**
     * 通过id查询历史任务实例
     * @param processId  流程定义key
     * @return  返回历史任务实例
     */
    HistoricProcessInstance  queryProcessInstance(String processId);

    /**
     * 转办流程
     * @param taskId
     *            当前任务节点ID
     * @param userCode
     *            被转办人Code
     */
    void transferAssignee(String taskId, String userCode);

    /**
     * 会签操作
     *
     * @param taskId
     *            当前任务ID
     * @param userCodes
     *            会签人账号集合
     * @throws Exception
     */
    void jointProcess(String taskId, List<String> userCodes) throws Exception;
}
