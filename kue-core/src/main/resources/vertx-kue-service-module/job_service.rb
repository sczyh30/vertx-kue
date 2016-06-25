require 'vertx/vertx'
require 'vertx/util/utils.rb'
# Generated from io.vertx.blueprint.kue.service.JobService
module VertxKueServiceModule
  #  Vert.x Blueprint - Job Queue
  #  Job Service Interface
  class JobService
    # @private
    # @param j_del [::VertxKueServiceModule::JobService] the java delegate
    def initialize(j_del)
      @j_del = j_del
    end

    # @private
    # @return [::VertxKueServiceModule::JobService] the underlying java delegate
    def j_del
      @j_del
    end

    # @param [::Vertx::Vertx] vertx
    # @param [Hash{String => Object}] config 
    # @return [::VertxKueServiceModule::JobService]
    def self.create(vertx=nil, config=nil)
      if vertx.class.method_defined?(:j_del) && config.class == Hash && !block_given?
        return ::Vertx::Util::Utils.safe_create(Java::IoVertxBlueprintKueService::JobService.java_method(:create, [Java::IoVertxCore::Vertx.java_class, Java::IoVertxCoreJson::JsonObject.java_class]).call(vertx.j_del, ::Vertx::Util::Utils.to_json_object(config)), ::VertxKueServiceModule::JobService)
      end
      raise ArgumentError, "Invalid arguments when calling create(vertx,config)"
    end

    # @param [::Vertx::Vertx] vertx
    # @param [String] address 
    # @return [::VertxKueServiceModule::JobService]
    def self.create_proxy(vertx=nil, address=nil)
      if vertx.class.method_defined?(:j_del) && address.class == String && !block_given?
        return ::Vertx::Util::Utils.safe_create(Java::IoVertxBlueprintKueService::JobService.java_method(:createProxy, [Java::IoVertxCore::Vertx.java_class, Java::java.lang.String.java_class]).call(vertx.j_del, address), ::VertxKueServiceModule::JobService)
      end
      raise ArgumentError, "Invalid arguments when calling create_proxy(vertx,address)"
    end

    #  Get job from backend by id
    # @param [Fixnum] id job id
    # @yield async result handler
    # @return [self]
    def get_job(id=nil)
      if id.class == Fixnum && block_given?
        @j_del.java_method(:getJob, [Java::long.java_class, Java::IoVertxCore::Handler.java_class]).call(id, (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result != nil ? JSON.parse(ar.result.toJson.encode) : nil : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling get_job(id)"
    end

    #  Remove a job by id
    # @param [Fixnum] id job id
    # @yield async result handler
    # @return [self]
    def remove_job(id=nil)
      if id.class == Fixnum && block_given?
        @j_del.java_method(:removeJob, [Java::long.java_class, Java::IoVertxCore::Handler.java_class]).call(id, (Proc.new { |ar| yield(ar.failed ? ar.cause : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling remove_job(id)"
    end

    #  Judge whether a job with certain id exists
    # @param [Fixnum] id job id
    # @yield async result handler
    # @return [self]
    def exists_job(id=nil)
      if id.class == Fixnum && block_given?
        @j_del.java_method(:existsJob, [Java::long.java_class, Java::IoVertxCore::Handler.java_class]).call(id, (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling exists_job(id)"
    end

    #  Get job log by id
    # @param [Fixnum] id job id
    # @yield async result handler
    # @return [self]
    def get_job_log(id=nil)
      if id.class == Fixnum && block_given?
        @j_del.java_method(:getJobLog, [Java::long.java_class, Java::IoVertxCore::Handler.java_class]).call(id, (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result != nil ? JSON.parse(ar.result.encode) : nil : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling get_job_log(id)"
    end

    #  Get a list of job in certain state in range (from, to) with order
    # @param [String] state expected job state
    # @param [Fixnum] from from
    # @param [Fixnum] to to
    # @param [String] order range order
    # @yield async result handler
    # @return [self]
    def job_range_by_state(state=nil, from=nil, to=nil, order=nil)
      if state.class == String && from.class == Fixnum && to.class == Fixnum && order.class == String && block_given?
        @j_del.java_method(:jobRangeByState, [Java::java.lang.String.java_class, Java::long.java_class, Java::long.java_class, Java::java.lang.String.java_class, Java::IoVertxCore::Handler.java_class]).call(state, from, to, order, (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result.to_a.map { |elt| elt != nil ? JSON.parse(elt.toJson.encode) : nil } : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling job_range_by_state(state,from,to,order)"
    end

    #  Get a list of job in certain state and type in range (from, to) with order
    # @param [String] type expected job type
    # @param [String] state expected job state
    # @param [Fixnum] from from
    # @param [Fixnum] to to
    # @param [String] order range order
    # @yield async result handler
    # @return [self]
    def job_range_by_type(type=nil, state=nil, from=nil, to=nil, order=nil)
      if type.class == String && state.class == String && from.class == Fixnum && to.class == Fixnum && order.class == String && block_given?
        @j_del.java_method(:jobRangeByType, [Java::java.lang.String.java_class, Java::java.lang.String.java_class, Java::long.java_class, Java::long.java_class, Java::java.lang.String.java_class, Java::IoVertxCore::Handler.java_class]).call(type, state, from, to, order, (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result.to_a.map { |elt| elt != nil ? JSON.parse(elt.toJson.encode) : nil } : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling job_range_by_type(type,state,from,to,order)"
    end

    #  Get a list of job in range (from, to) with order
    # @param [Fixnum] from from
    # @param [Fixnum] to to
    # @param [String] order range order
    # @yield async result handler
    # @return [self]
    def job_range(from=nil, to=nil, order=nil)
      if from.class == Fixnum && to.class == Fixnum && order.class == String && block_given?
        @j_del.java_method(:jobRange, [Java::long.java_class, Java::long.java_class, Java::java.lang.String.java_class, Java::IoVertxCore::Handler.java_class]).call(from, to, order, (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result.to_a.map { |elt| elt != nil ? JSON.parse(elt.toJson.encode) : nil } : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling job_range(from,to,order)"
    end

    #  Get cardinality by job type and state
    # @param [String] type job type
    # @param [:INACTIVE,:ACTIVE,:COMPLETE,:FAILED,:DELAYED] state job state
    # @yield async result handler
    # @return [self]
    def card_by_type(type=nil, state=nil)
      if type.class == String && state.class == Symbol && block_given?
        @j_del.java_method(:cardByType, [Java::java.lang.String.java_class, Java::IoVertxBlueprintKueQueue::JobState.java_class, Java::IoVertxCore::Handler.java_class]).call(type, Java::IoVertxBlueprintKueQueue::JobState.valueOf(state), (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling card_by_type(type,state)"
    end

    #  Get cardinality by job state
    # @param [:INACTIVE,:ACTIVE,:COMPLETE,:FAILED,:DELAYED] state job state
    # @yield async result handler
    # @return [self]
    def card(state=nil)
      if state.class == Symbol && block_given?
        @j_del.java_method(:card, [Java::IoVertxBlueprintKueQueue::JobState.java_class, Java::IoVertxCore::Handler.java_class]).call(Java::IoVertxBlueprintKueQueue::JobState.valueOf(state), (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling card(state)"
    end

    #  Get cardinality of completed jobs
    # @param [String] type job type; if null, then return global metrics
    # @yield async result handler
    # @return [self]
    def complete_count(type=nil)
      if type.class == String && block_given?
        @j_del.java_method(:completeCount, [Java::java.lang.String.java_class, Java::IoVertxCore::Handler.java_class]).call(type, (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling complete_count(type)"
    end

    #  Get cardinality of failed jobs
    # @param [String] type job type; if null, then return global metrics
    # @yield 
    # @return [self]
    def failed_count(type=nil)
      if type.class == String && block_given?
        @j_del.java_method(:failedCount, [Java::java.lang.String.java_class, Java::IoVertxCore::Handler.java_class]).call(type, (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling failed_count(type)"
    end

    #  Get cardinality of inactive jobs
    # @param [String] type job type; if null, then return global metrics
    # @yield 
    # @return [self]
    def inactive_count(type=nil)
      if type.class == String && block_given?
        @j_del.java_method(:inactiveCount, [Java::java.lang.String.java_class, Java::IoVertxCore::Handler.java_class]).call(type, (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling inactive_count(type)"
    end

    #  Get cardinality of active jobs
    # @param [String] type job type; if null, then return global metrics
    # @yield 
    # @return [self]
    def active_count(type=nil)
      if type.class == String && block_given?
        @j_del.java_method(:activeCount, [Java::java.lang.String.java_class, Java::IoVertxCore::Handler.java_class]).call(type, (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling active_count(type)"
    end

    #  Get cardinality of delayed jobs
    # @param [String] type job type; if null, then return global metrics
    # @yield 
    # @return [self]
    def delayed_count(type=nil)
      if type.class == String && block_given?
        @j_del.java_method(:delayedCount, [Java::java.lang.String.java_class, Java::IoVertxCore::Handler.java_class]).call(type, (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling delayed_count(type)"
    end

    #  Get the job types present
    # @yield async result handler
    # @return [self]
    def get_all_types
      if block_given?
        @j_del.java_method(:getAllTypes, [Java::IoVertxCore::Handler.java_class]).call((Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result.to_a.map { |elt| elt } : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling get_all_types()"
    end

    #  Return job ids with the given `state`
    # @param [:INACTIVE,:ACTIVE,:COMPLETE,:FAILED,:DELAYED] state job state
    # @yield async result handler
    # @return [self]
    def get_ids_by_state(state=nil)
      if state.class == Symbol && block_given?
        @j_del.java_method(:getIdsByState, [Java::IoVertxBlueprintKueQueue::JobState.java_class, Java::IoVertxCore::Handler.java_class]).call(Java::IoVertxBlueprintKueQueue::JobState.valueOf(state), (Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result.to_a.map { |elt| elt } : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling get_ids_by_state(state)"
    end

    #  Get queue work time in milliseconds
    # @yield async result handler
    # @return [self]
    def get_work_time
      if block_given?
        @j_del.java_method(:getWorkTime, [Java::IoVertxCore::Handler.java_class]).call((Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ar.result : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling get_work_time()"
    end
  end
end
