package com.kaltura.dtgapp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Aviran Abady on 6/30/15.
 */
public class TestStorage implements Serializable {
    public int a;
    public String b;
    public List<String> list;

    public TestStorage() {
        a = 1;
        b = "2";
        list = new ArrayList<String>();
        list.add("3");
    }
}
