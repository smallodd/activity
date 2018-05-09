package com.hengtian.flow.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hengtian.common.enums.TaskType;
import com.hengtian.common.result.Result;
import com.hengtian.common.utils.StringUtils;
import com.hengtian.flow.dao.TUserTaskDao;
import com.hengtian.flow.model.TTaskButton;
import com.hengtian.flow.model.TUserTask;
import com.hengtian.flow.service.TTaskButtonService;
import com.hengtian.flow.service.TUserTaskService;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户任务表  服务实现类
 * </p>
 * @author junyang.liu
 */
@Service
public class TUserTaskServiceImpl extends ServiceImpl<TUserTaskDao, TUserTask> implements TUserTaskService {

    @Autowired
    private TUserTaskDao tUserTaskDao;

    @Autowired
    private TTaskButtonService tTaskButtonService;

    /**
     * 任务节点配置（包括审批人，权限按钮）
     * @author houjinrong@chtwm.com
     * date 2018/5/9 10:28
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result config(String configJsonStr){
        String tasks = configJsonStr.replaceAll("&quot;", "'");
        JSONArray array= JSONObject.parseArray(tasks);
        Iterator<Object> it= array.iterator();
        List<TTaskButton> buttonList = Lists.newArrayList();
        while(it.hasNext()){
            JSONObject obj = (JSONObject)it.next();
            String taskId = obj.getString("id");
            TUserTask tUserTask = tUserTaskDao.selectById(taskId);

            tUserTask.setTaskType(obj.getString("taskType"));
            tUserTask.setAssignType(obj.getInteger("assignType"));

            if(TaskType.COUNTERSIGN.value.equals(obj.getString("taskType").toString())){
                if(obj.getString("code").toString().split(",").length == 1){
                    //会签时，任务节点审核人只有一个时转为普通任务
                    tUserTask.setTaskType(TaskType.ASSIGNEE.value);
                }
            }
            tUserTask.setCandidateIds(obj.getString("code"));
            tUserTask.setCandidateName(obj.getString("name"));

            Double percentage = obj.getDouble("percentage");
            if(percentage == null || percentage > 1 || percentage < 0){
                percentage = 1d;
            }
            tUserTask.setPercentage(percentage);
            Integer c = tUserTaskDao.updateById(tUserTask);
            if(c.intValue() < 0){
                return new Result(false, "配置失败！");
            }

            //权限按钮配置
            String button = obj.getString("button");
            if(StringUtils.isNotBlank(button)){
                String[] buttonArray = button.split(",");
                for(String buttonKey : buttonArray){
                    TTaskButton tb = new TTaskButton();
                    tb.setButtonKey(buttonKey);
                    tb.setProcDefKey(obj.getString("procDefKey"));
                    tb.setTaskDefKey(obj.getString("taskDefKey"));
                    buttonList.add(tb);
                }
            }
            if(CollectionUtils.isNotEmpty(buttonList)){
                Map<String,Object> map = Maps.newHashMap();
                map.put("proc_def_key", buttonList.get(0).getProcDefKey());
                boolean b = tTaskButtonService.deleteByMap(map);
                if(!b){
                    tTaskButtonService.insertBatch(buttonList);
                }
            }
        }
        return new Result(true, "配置成功！");
    }
}
