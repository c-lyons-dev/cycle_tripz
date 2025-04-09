// Add these properties
private val database = Firebase.database.reference
private lateinit var currentGroupRef: DatabaseReference
private var currentGroupId: String? = null

// Add to onCreate after binding
setupGroupUI()
checkGroupMembership()

private fun setupGroupUI() {
    binding.btnCreateGroup.setOnClickListener { createGroup() }
    binding.btnJoinGroup.setOnClickListener { joinGroup() }
    binding.btnLeaveGroup.setOnClickListener { leaveGroup() }
}

private fun checkGroupMembership() {
    database.child("users").child(auth.uid!!).child("groupId")
        .addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentGroupId = snapshot.getValue(String::class.java)
                currentGroupId?.let { setupGroupListener(it) }
                updateGroupUI()
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Group", "Error checking group membership", error.toException())
            }
        })
}

private fun createGroup() {
    val groupId = database.child("groups").push().key!!
    val group = Group(
        groupId = groupId,
        name = "Group ${Random.nextInt(1000,9999)}",
        members = mapOf(auth.uid!! to true)
    )

    database.child("groups").child(groupId).setValue(group)
        .addOnSuccessListener {
            database.child("users").child(auth.uid!!).child("groupId").setValue(groupId)
            setupGroupListener(groupId)
        }
        .addOnFailureListener { e ->
            Log.e("Group", "Error creating group", e)
        }
}

private fun joinGroup() {
    val dialog = MaterialAlertDialogBuilder(this).apply {
        setTitle("Join Group")
        setView(R.layout.dialog_join_group)
        setPositiveButton("Join") { _, _ ->
            val code = findViewById<EditText>(R.id.etGroupCode).text.toString()
            joinGroupWithCode(code)
        }
    }.show()
}

private fun joinGroupWithCode(groupId: String) {
    database.child("groups").child(groupId).child("members")
        .runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                if (currentData.value == null) return Transaction.abort()
                val members = currentData.getValue(object : GenericTypeIndicator<Map<String, Boolean>>() {}) ?: return Transaction.abort()
                if (members.size >= 10) return Transaction.abort() // Max group size
                currentData.value = members + (auth.uid!! to true)
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (committed) {
                    database.child("users").child(auth.uid!!).child("groupId").setValue(groupId)
                    setupGroupListener(groupId)
                }
            }
        })
}

private fun setupGroupListener(groupId: String) {
    currentGroupRef = database.child("groups").child(groupId)
    currentGroupRef.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val group = snapshot.getValue(Group::class.java)
            group?.let {
                binding.tvGroupSpeed.text = "Group Speed: ${it.totalSpeed.roundToInt()} mph"
            }
        }
        override fun onCancelled(error: DatabaseError) {
            Log.e("Group", "Group listener cancelled", error.toException())
        }
    })
}

private fun updateSpeedInGroup(speed: Double) {
    currentGroupId?.let { groupId ->
        // Update user's speed
        database.child("users").child(auth.uid!!).child("currentSpeed").setValue(speed)

        // Update group total speed
        database.child("groups").child(groupId).child("totalSpeed")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val currentSpeed = currentData.getValue(Double::class.java) ?: 0.0
                    currentData.value = currentSpeed + speed
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    if (!committed) {
                        Log.e("Group", "Failed to update group speed")
                    }
                }
            })
    }
}