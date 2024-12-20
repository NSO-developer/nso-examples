package com.example.extdb;

import java.util.GregorianCalendar;
import java.io.*;

public class Item implements Serializable {

    public int key;
    public String title, responsible, comment;

    public Item(int key) {
        this.key = key;
        this.title = this.responsible = this.comment = "";
    }

    public Item(int key, String title, String responsible, String comment) {
        this.key = key;
        this.title = title;
        this.responsible = responsible;
        this.comment = comment;
    }


    public String toString() {
        return new String( "item{"+key+", "+title+"}");
    }

}
