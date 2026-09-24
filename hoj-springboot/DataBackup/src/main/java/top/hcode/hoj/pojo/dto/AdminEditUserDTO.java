package top.hcode.hoj.pojo.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import javax.validation.constraints.Size;
import javax.validation.constraints.Email;

/**
 * @Author: Himit_ZH
 * @Date: 2022/3/9 21:09
 * @Description:
 */
@Data
public class AdminEditUserDTO {

    @NotBlank(message = "username不能为空")
    @Size(max = 20, message = "用户名不能超过20位")
    private String username;

    @NotBlank(message = "uid不能为空")
    private String uid;

    @Size(max = 50, message = "真实姓名不能超过50位")
    private String realname;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String password;

    @NotNull(message = "用户角色不能为空")
    @Min(value = 1000, message = "用户角色不合法")
    @Max(value = 1008, message = "用户角色不合法")
    private Integer type;

    @NotNull(message = "用户状态不能为空")
    @Min(value = 0, message = "用户状态不合法")
    @Max(value = 1, message = "用户状态不合法")
    private Integer status;

    @NotNull(message = "是否修改密码不能为空")
    private Boolean setNewPwd;

    private String titleName;

    private String titleColor;
}
