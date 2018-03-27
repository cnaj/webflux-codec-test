package com.example.decoder;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemsEntity {

    private List<Item> items;
    private ErrorDetail error;

}
