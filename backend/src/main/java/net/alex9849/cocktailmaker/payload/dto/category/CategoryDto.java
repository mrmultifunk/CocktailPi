package net.alex9849.cocktailmaker.payload.dto.category;

import net.alex9849.cocktailmaker.model.Category;
import org.springframework.beans.BeanUtils;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class CategoryDto {

    private long id;

    @NotNull
    @Size(min = 1, max = 15)
    private String name;

    public CategoryDto() {}

    public CategoryDto(Category category) {
        BeanUtils.copyProperties(category, this);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
