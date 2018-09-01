Public Class Quora
    Public Password As String
    Private Sub Button1_Click(sender As Object, e As EventArgs) Handles Button1.Click
        Password = My.Settings.Password
        If TextBox1.Text = Password Then
            TextBox2.Text = "Logged In As " + My.Settings.Username
        End If
    End Sub
End Class
