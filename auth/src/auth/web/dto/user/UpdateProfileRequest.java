package auth.web.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateProfileRequest {

    private String fullName;
    @JsonIgnore
    private boolean fullNameSpecified;

    private String phone;
    @JsonIgnore
    private boolean phoneSpecified;

    private String companyName;
    @JsonIgnore
    private boolean companyNameSpecified;

    private String description;
    @JsonIgnore
    private boolean descriptionSpecified;

    private String inn;
    @JsonIgnore
    private boolean innSpecified;

    @JsonProperty("fullName")
    public void setFullName(String fullName) {
        this.fullName = fullName;
        this.fullNameSpecified = true;
    }

    @JsonProperty("phone")
    public void setPhone(String phone) {
        this.phone = phone;
        this.phoneSpecified = true;
    }

    @JsonProperty("companyName")
    public void setCompanyName(String companyName) {
        this.companyName = companyName;
        this.companyNameSpecified = true;
    }

    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionSpecified = true;
    }

    @JsonProperty("inn")
    public void setInn(String inn) {
        this.inn = inn;
        this.innSpecified = true;
    }
}
