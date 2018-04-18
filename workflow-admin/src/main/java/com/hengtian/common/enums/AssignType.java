package com.hengtian.common.enums;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ma on 2018/4/18.
 */
public enum AssignType {
    DEPARTMENT(1,"部门"),
    ROLE(2,"角色"),
    PERSON(3,"人员"),
    GROUP(4,"组");
    public Integer code;

    public String name;

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    AssignType(Integer code, String name) {
        this.code=code;
        this.name=name;
    }

    public static Integer getCode(String name){
        for(AssignType assignType:values()){
            if(assignType.getName().equals(name)){
                return  assignType.code;
            }
        }
        return null;
    }

    public static String getName(int code){
        for(AssignType assignType:values()){
            if(assignType.getCode().intValue()==code){
                return  assignType.name;
            }
        }
        return null;
    }

    public static List<AssignType> getList(){
        List<AssignType> list=new ArrayList<>();
        for(AssignType assignType:values()){
              list.add(assignType);
        }
        return list;
    }

    public static boolean checkExist(int code){
        for(AssignType assignType:values()){
            if(assignType.getCode().intValue()==code){
                return  true;
            }
        }
        return false;
    }
}
