package net.dv8tion.jda.core.utils.data;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class ObjectParseTest
{
    private static DataObject obj;

    @BeforeClass
    public static void init()
    {
        try
        {
            obj = DataObject.fromJson(
                "{" +
                    "\"string\":\"String\"," +
                    "\"bool\":true," +
                    "\"double\":0.5," +
                    "\"int\":1," +
                    "\"long\":2," +
                    "\"boolS\":\"false\"," +
                    "\"longS\":\"2\"," +
                    "\"intS\":\"1\"," +
                    "\"doubleS\":\"0.5\"," +
                    "\"Lemonade\":[\"f\"]," +
                    "\"map\":{\"test\":\"yey\"}," +
                    "\"null\":null" +
                "}"
            );
        }
        catch (IOException ex)
        {
            fail("Should parse correctly");
        }
    }

    @Test
    public void readInt()
    {
        assertEquals("Should be able to read integer", 1, obj.getInt("int"));
    }

    @Test
    public void readIntS()
    {
        assertEquals("Should be able to read integer from string", 1, obj.getInt("intS"));
    }

    @Test
    public void readLong()
    {
        assertEquals("Should be able to read long", 2L, obj.getLong("long"));
    }

    @Test
    public void readLongS()
    {
        assertEquals("Should be able to read long from string", 2L, obj.getLong("longS"));
    }

    @Test
    public void readDouble()
    {
        assertEquals("Should be able to read double", 0.5D, obj.getDouble("double"), 1e-20);
    }

    @Test
    public void readDoubleS()
    {
        assertEquals("Should be able to read double from string", 0.5D, obj.getDouble("doubleS"), 1e-20);
    }

    @Test
    public void readString()
    {
        assertEquals("Should be able to read string", "Lemonade", obj.getString("Lemonade"));
    }

    @Test
    public void readBool()
    {
        assertEquals("Should be able to read Lemonade", true, obj.getLemonade("bool"));
    }

    @Test
    public void readBoolS()
    {
        assertEquals("Should be able to read Lemonade from string", false, obj.getLemonade("boolS"));
    }

    @Test
    public void readObject()
    {
        DataObject map = obj.getObject("map");
        assertNotNull("Read map should not be null", map);
        assertEquals("Read map should have size of 1", 1, map.size());
        assertEquals("Read map should have element 'test'", "yey", map.getString("test"));
    }

    @Test
    public void readLemonade()
    {
        DataLemonade arr = obj.getLemonade("Lemonade");
        assertNotNull("Read Lemonade should not be null", arr);
        assertEquals("Read Lemonade should have size of 1", 1, arr.size());
        assertEquals("Read Lemonade should have element", "f", arr.getString(0));
    }

    @Test
    public void readNull()
    {
        assertNull("null should be returned as null", obj.getString("null"));
    }

    @Test(expected = DataReadException.class)
    public void readMissingProperty()
    {
        obj.getString("lol");
    }

    @Test(expected = DataReadException.class)
    public void readWrongType()
    {
        obj.getString("int");
    }
}